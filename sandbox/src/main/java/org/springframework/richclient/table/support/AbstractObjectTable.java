/*
 * Copyright 2002-2006 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.springframework.richclient.table.support;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;

import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.support.PropertyComparator;
import org.springframework.binding.value.ValueModel;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.richclient.application.event.LifecycleApplicationEvent;
import org.springframework.richclient.application.support.ApplicationEventRedispatcher;
import org.springframework.richclient.application.support.ApplicationServicesAccessor;
import org.springframework.richclient.command.ActionCommandExecutor;
import org.springframework.richclient.command.CommandGroup;
import org.springframework.richclient.command.GuardedActionCommandExecutor;
import org.springframework.richclient.list.ListSelectionValueModelAdapter;
import org.springframework.richclient.progress.StatusBarCommandGroup;
import org.springframework.richclient.util.PopupMenuMouseListener;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.SortedList;
import ca.odell.glazedlists.event.ListEvent;
import ca.odell.glazedlists.event.ListEventListener;
import ca.odell.glazedlists.gui.TableFormat;
import ca.odell.glazedlists.swing.EventSelectionModel;
import ca.odell.glazedlists.swing.TableComparatorChooser;

/**
 * This class provides a standard table representation for a set of objects with
 * properties of the objects presented in the columns of the table. The table created
 * offers the following features:
 * <ol>
 * <li>It uses Glazed Lists as the underlying data model and this provides for
 * multi-column sorting and text filtering.</li>
 * <li>It handles row selection.</>
 * <li>It offers simple, delegated handling of how to handle a double-click on a row, by
 * setting a command executor. See {@link #setDoubleClickHandler(ActionCommandExecutor)}.</li>
 * <li>It supports display of a configured pop-up context menu.</li>
 * <li>It can report on row counts (after filtering) and selection counts to a status bar</li>
 * </ol>
 * <p>
 * Several I18N messages are needed for proper reporting to a configured status bar. The
 * message keys used are:
 * <p>
 * <table border="1">
 * <tr>
 * <td><b>Message key </b></td>
 * <td><b>Usage </b></td>
 * </tr>
 * <tr>
 * <td><i>modelId</i>.objectName.singular</td>
 * <td>The singular name of the objects in the table</td>
 * </tr>
 * <tr>
 * <td><i>modelId</i>.objectName.plural</td>
 * <td>The plural name of the objects in the table</td>
 * </tr>
 * <tr>
 * <td><i>[modelId]</i>.objectTable.showingAll.message</td>
 * <td>The message to show when all objects are being shown, that is no objects have been
 * filtered. This is typically something like "Showing all nn contacts". The message takes
 * the number of objects nd the object name (singular or plural) as parameters.</td>
 * </tr>
 * <tr>
 * <td><i>[modelId]</i>.objectTable.showingN.message</td>
 * <td>The message to show when some of the objects have been filtered from the display.
 * This is typically something like "Showing nn contacts of nn". The message takes the
 * shown count, the total count, and the object name (singular or plural) as parameters.</td>
 * </tr>
 * <tr>
 * <td><i>[modelId]</i>.objectTable.selectedN.message</td>
 * <td>The message to append to the filter message when the selection is not empty.
 * Typically something like ", nn selected". The message takes the number of selected
 * entries as a parameter.</td>
 * </tr>
 * </table>
 * <p>
 * Note that the message keys that show the model id in brackets, like this <i>[modelId]</i>,
 * indicate that the model id is optional. If no message is found using the model id, then
 * the key will be tried without the model id and the resulting string will be used. This
 * makes it easy to construct one single message property that can be used on numerous
 * tables.
 * <p>
 * <em>Note:</em> If you are using application events to inform UI components of changes
 * to domain objects, then instances of this class have to be wired into the event
 * distribution. To do this, you should construct instances (of concrete subclasses) in
 * the application context. They will automatically be wired into the epplication event
 * mechanism because this class implements {@link ApplicationListener}.
 * 
 * @author Larry Streepy
 */
public abstract class AbstractObjectTable extends ApplicationServicesAccessor implements ListEventListener {

    private final Log _logger = LogFactory.getLog(getClass());

    private String modelId;
    private String objectSingularName;
    private String objectPluralName;
    private Object[] initialData = null;
    private String[] columnPropertyNames;
    private GlazedTableModel model;
    private JTable table;
    private SortedList baseList;
    private EventList finalEventList;
    private ActionCommandExecutor doubleClickHandler;
    private CommandGroup popupCommandGroup;
    private StatusBarCommandGroup statusBar;

    public static final String SHOWINGALL_MSG_KEY = "objectTable.showingAll.message";
    public static final String SHOWINGN_MSG_KEY = "objectTable.showingN.message";
    public static final String SELECTEDN_MSG_KEY = "objectTable.selectedN.message";

    /**
     * Constructor.
     * 
     * @param modelId used for generating message keys
     * @param objectType The type of object held in the table
     */
    public AbstractObjectTable( String modelId, String[] columnPropertyNames ) {
        this.modelId = modelId;
        setColumnPropertyNames(columnPropertyNames);
        init();
    }

    /**
     * Initialize our internal values.
     */
    protected void init() {
        // Get all our messages

        objectSingularName = getMessage(modelId + ".objectName.singular");
        objectPluralName = getMessage(modelId + ".objectName.plural");
    }

    /**
     * Set the initial data to display.
     * 
     * @param initialData Array of objects to display
     */
    public void setInitialData( Object[] initialData ) {
        this.initialData = initialData;
    }

    /**
     * Get the initial data to display. If none has been set, then return the default
     * initial data.
     * 
     * @return initial data to display
     * @see #getDefaultInitialData()
     */
    public Object[] getInitialData() {
        if( initialData == null ) {
            initialData = getDefaultInitialData();
        }
        return initialData;
    }

    /**
     * Get the base event list for the table model. This can be used to build layered
     * event models for filtering.
     * 
     * @return base event list
     */
    public EventList getBaseEventList() {
        if( baseList == null ) {
            // Construct on demand
            Object[] data = getInitialData();

            if( _logger.isInfoEnabled() ) {
                _logger.info("Table data: got " + data.length + " entries");
            }

            // Construct the event list of all our data and layer on the sorting
            EventList rawList = new BasicEventList();
            rawList.addAll(Arrays.asList(data));
            String sortProperty = getColumnPropertyNames()[getInitialSortColumn()];
            baseList = new SortedList(rawList, new PropertyComparator(sortProperty, false, true));
        }
        return baseList;
    }

    /**
     * Set the event list to be used for constructing the table model. The event list
     * provided MUST have been constructed from the list returned by
     * {@link #getBaseEventList()} or this table will not work properly.
     * 
     * @param event list to use
     */
    public void setFinalEventList( EventList finalEventList ) {
        this.finalEventList = finalEventList;
    }

    /**
     * Get the event list to be use for constructing the table model.
     * 
     * @return final event list
     */
    public EventList getFinalEventList() {
        if( finalEventList == null ) {
            finalEventList = getBaseEventList();
        }
        return finalEventList;
    }

    /**
     * Get the table control.
     * 
     * @return JTable instance all configured and ready to go
     */
    public JTable getTable() {
        if( table == null ) {
            createTable();
        }
        return table;
    }

    /**
     * Create our control.
     */
    public void createTable() {

        // Contstruct the table model and table to display the data
        EventList finalEventList = getFinalEventList();
        model = createTableModel(finalEventList);

        table = new JTable(model);
        table.setSelectionModel(new EventSelectionModel(finalEventList));
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // Install the sorter
        TableComparatorChooser tableSorter = new TableComparatorChooser(table, baseList, true);

        // Allow the derived type to configure the table
        configureTable(table);

        // Sort on the last name by default
        tableSorter.clearComparator();
        tableSorter.appendComparator(getInitialSortColumn(), 0, false);

        // Add the context menu listener
        table.addMouseListener(new PopupMenuMouseListener() {
            protected JPopupMenu getPopupMenu() {
                return createPopupContextMenu();
            }
        });

        // Add our mouse handlers to setup our desired selection mechanics
        table.addMouseListener(new MouseAdapter() {
            public void mousePressed( MouseEvent e ) {
                // If the user right clicks on a row other than the selection,
                // then move
                // the selection to the current row
                int rowUnderMouse = table.rowAtPoint(e.getPoint());
                if( e.getButton() == MouseEvent.BUTTON3 && !table.isRowSelected(rowUnderMouse) ) {
                    // Select the row under the mouse
                    if( rowUnderMouse != -1 ) {
                        table.getSelectionModel().setSelectionInterval(rowUnderMouse, rowUnderMouse);
                    }
                }
            }

            /**
             * Handle double click.
             */
            public void mouseClicked( MouseEvent e ) {
                // If the user double clicked on a row, then open the properties
                if( e.getClickCount() == 2 ) {
                    onDoubleClick();
                }
            }
        });

        // Keep our status line up to date with the selections and filtering
        getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged( ListSelectionEvent e ) {
                updateStatusBar();
            }
        });

        getFinalEventList().addListEventListener(this);
    }

    /**
     * Handle a double click on a row of the table. The row will already be selected.
     */
    protected void onDoubleClick() {
        // Dispatch this to the doubleClickHandler, if any
        if( doubleClickHandler != null ) {
            boolean okToExecute = true;
            if( doubleClickHandler instanceof GuardedActionCommandExecutor ) {
                okToExecute = ((GuardedActionCommandExecutor) doubleClickHandler).isEnabled();
            }

            if( okToExecute ) {
                doubleClickHandler.execute();
            }
        }
    }

    /**
     * Construct the table model for this table. The default implementation of this
     * creates a GlazedTableModel using an Advanced format.
     * 
     * @param eventList on which to build the model
     * @return table model
     */
    protected GlazedTableModel createTableModel( EventList eventList ) {
        return new GlazedTableModel(eventList, getMessageSource(), getColumnPropertyNames()) {
            protected TableFormat createTableFormat() {
                return new DefaultAdvancedTableFormat();
            }
        };
    }

    /**
     * Get the data model for the table.
     * 
     * @return model
     */
    public GlazedTableModel getTableModel() {
        return model;
    }

    /**
     * Get the names of the properties to display in the table columns.
     * 
     * @return array of columnproperty names
     */
    public String[] getColumnPropertyNames() {
        return columnPropertyNames;
    }

    /**
     * Set the names of the properties to display in the table columns.
     * 
     * @param columnPropertyNames
     */
    public void setColumnPropertyNames( String[] columnPropertyNames ) {
        this.columnPropertyNames = columnPropertyNames;
    }

    /**
     * @return the doubleClickHandler
     */
    public ActionCommandExecutor getDoubleClickHandler() {
        return doubleClickHandler;
    }

    /**
     * Set the handler (action executor) that should be invoked when a row in the table is
     * double-clicked.
     * 
     * @param doubleClickHandler the doubleClickHandler to set
     */
    public void setDoubleClickHandler( ActionCommandExecutor doubleClickHandler ) {
        this.doubleClickHandler = doubleClickHandler;
    }

    /**
     * @return the popupCommandGroup
     */
    public CommandGroup getPopupCommandGroup() {
        return popupCommandGroup;
    }

    /**
     * Set the command group that should be used to construct the popup menu when a user
     * initiates the UI gesture to show the context menu. If this is null, then no popup
     * menu will be shown.
     * 
     * @param popupCommandGroup the popupCommandGroup to set
     */
    public void setPopupCommandGroup( CommandGroup popupCommandGroup ) {
        this.popupCommandGroup = popupCommandGroup;
    }

    /**
     * Configure the newly created table as needed. Install any needed column sizes,
     * renderers, and comparators. The default implementation does nothing.
     * 
     * @param table The table to configure
     */
    protected void configureTable( JTable table ) {
    }

    /**
     * Get the default set of objects for this table.
     * 
     * @return Array of data for the table
     */
    protected abstract Object[] getDefaultInitialData();

    /**
     * Determine if the event should be handled on this table. If <code>true</code> is
     * returned (the default), then the list holding the table data will be scanned for
     * the object and updated appropriately depending on then event type.
     * 
     * @param event to inspect
     * @return boolean true if the object should be handled, false otherwise
     * @see #handleDeletedObject(Object)
     * @see #handleNewObject(Object)
     * @see #handleUpdatedObject(Object)
     * 
     */
    protected boolean shouldHandleEvent( ApplicationEvent event ) {
        return true;
    }

    /**
     * Create the context popup menu, if any, for this table. The default operation is to
     * create the popup from the command group if one has been specified. If not, then
     * null is returned.
     * 
     * @return popup menu to show, or null if none
     */
    protected JPopupMenu createPopupContextMenu() {
        return (getPopupCommandGroup() != null) ? getPopupCommandGroup().createPopupMenu() : null;
    }

    /**
     * Get the default sort column. Defaults to 0.
     * 
     * @return column to sort on
     */
    protected int getInitialSortColumn() {
        return 0;
    }

    /**
     * Get the selection model.
     * 
     * @return selection model
     */
    public ListSelectionModel getSelectionModel() {
        return getTable().getSelectionModel();
    }

    /**
     * Handle the creation of a new object.
     * 
     * @param object New object to handle
     */
    protected void handleNewObject( Object object ) {
        getFinalEventList().add(object);
    }

    /**
     * Handle an updated object in this table. Locate the existing entry (by equals) and
     * replace it in the underlying list.
     * 
     * @param object Updated object to handle
     */
    protected void handleUpdatedObject( Object object ) {
        int index = baseList.indexOf(object);
        if( index >= 0 ) {
            baseList.set(index, object);
        }
    }

    /**
     * Handle the deletion of an object in this table. Locate this entry (by equals) and
     * delete it.
     * 
     * @param object Updated object being deleted
     */
    protected void handleDeletedObject( Object object ) {
        int index = baseList.indexOf(object);
        if( index >= 0 ) {
            baseList.remove(index);
        }
    }

    /**
     * Set the status bar associated with this table. If non-null, then any time the final
     * event list on this table changes, then the status bar will be updated with the
     * current object counts.
     * 
     * @param statusBar to update
     */
    public void setStatusBar( StatusBarCommandGroup statusBar ) {
        this.statusBar = statusBar;
    }

    /*
     * (non-Javadoc)
     * 
     * @see ca.odell.glazedlists.event.ListEventListener#listChanged(ca.odell.glazedlists.event.ListEvent)
     */
    public void listChanged( ListEvent listChanges ) {
        // Our object list has changed, so update the status bar
        updateStatusBar();
    }

    /**
     * Update the status bar with the current display counts.
     */
    protected void updateStatusBar() {
        if( statusBar != null ) {
            int all = getBaseEventList().size();
            int showing = getFinalEventList().size();
            String msg;
            if( all == showing ) {
                String[] keys = new String[] { modelId + "." + SHOWINGALL_MSG_KEY, SHOWINGALL_MSG_KEY };
                msg = getMessage(keys, new Object[] { ""+all, (all != 1) ? objectPluralName : objectSingularName } );
            } else {
                String[] keys = new String[] { modelId + "." + SHOWINGN_MSG_KEY, SHOWINGN_MSG_KEY };

                msg = getMessage(keys, new Object[] { ""+showing, (showing != 1) ? objectPluralName : objectSingularName, ""+all } );
            }

            // Now add the selection info
            int nselected = table.getSelectedRowCount();
            if( nselected > 0 ) {
                String[] keys = new String[] { modelId + "." + SELECTEDN_MSG_KEY, SELECTEDN_MSG_KEY };

                msg += getMessage(keys, new Object[] { ""+nselected } );
            }

            statusBar.setMessage(msg.toString());
        }
    }

    /**
     * Set the event redispatcher. This will be configured via the application context.
     * When this is set, we add our handler so that we will receive proper application
     * event notification.
     * 
     * @param dispatcher
     */
    public void setApplicationEventRedispatcher( ApplicationEventRedispatcher dispatcher ) {
        dispatcher.addListener(new ApplicationEventHandler());
    }

    /**
     * This class will handle the application event dispatching. It might seem correct to
     * just have the main class implement ApplicationListener, but that will lead to
     * problems since instances of this class are commonly created in the application
     * context as prototypes (non-singletons) which leads to an extra instance being
     * created that is not fully initialized.
     * <p>
     * This stems from the way that the ApplicationListener tag interface is handled
     * within the application context. After all the bean definitions have been
     * internalized, the context looks for any beans that implement the
     * ApplicationListener interface. It then obtains the bean and registers it as a
     * listener. Since object tables are typically prototypes, this creates an instance of
     * the table that is not tied into any other object and has not been completely
     * initialized.
     * <p>
     * So, to avoid all that, we simply use an internal class to handle the events.
     */
    private class ApplicationEventHandler implements ApplicationListener {
        /**
         * Handle an application event. This will notify us of object adds, deletes, and
         * modifications. Update our table model accordingly.
         * 
         * @param e event to process
         */
        public void onApplicationEvent( ApplicationEvent e ) {
            if( e instanceof LifecycleApplicationEvent ) {
                LifecycleApplicationEvent le = (LifecycleApplicationEvent) e;
                if( shouldHandleEvent(e) ) {
                    if( le.getEventType() == LifecycleApplicationEvent.CREATED ) {
                        handleNewObject(le.getObject());
                    } else if( le.getEventType() == LifecycleApplicationEvent.MODIFIED ) {
                        handleUpdatedObject(le.getObject());
                    } else if( le.getEventType() == LifecycleApplicationEvent.DELETED ) {
                        handleDeletedObject(le.getObject());
                    }
                }
            }
        }
    }
}