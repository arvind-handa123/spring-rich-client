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
package org.springframework.richclient.form.binding.swing;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JComponent;
import javax.swing.ListModel;

import org.springframework.binding.form.FormModel;
import org.springframework.binding.value.ValueModel;
import org.springframework.binding.value.support.ListListModel;
import org.springframework.core.ReflectiveVisitorHelper;
import org.springframework.core.closure.Constraint;
import org.springframework.richclient.form.binding.support.AbstractBinding;
import org.springframework.richclient.list.AbstractFilteredListModel;
import org.springframework.richclient.list.FilteredListModel;
import org.springframework.richclient.list.SortedListModel;
import org.springframework.util.Assert;

/**
 * @author Mathias Broekelmann
 * 
 */
public abstract class AbstractListBinding extends AbstractBinding {

    private JComponent component;

    final ReflectiveVisitorHelper visitor = new ReflectiveVisitorHelper();

    private final SelectableItemsVisitor selectableItemsVisitor = new SelectableItemsVisitor();

    private Object selectableItems;

    private Comparator comparator;

    private Constraint filter;

    private final FilterConstraint filterConstraint = new FilterConstraint();

    private final Observer filterObserver = new FilterObserver();

    private final BindingComparator bindingComparator = new BindingComparator();

    private ListModel bindingModel;

    private AbstractFilteredListModel filteredModel;

    private final Observer comparatorObserver = new ComparatorObserver();

    public AbstractListBinding(JComponent component, FormModel formModel, String formPropertyPath,
            Class requiredSourceClass) {
        super(formModel, formPropertyPath, requiredSourceClass);
        this.component = component;
    }

    protected void enabledChanged() {
        component.setEnabled(!isReadOnly() && isEnabled());
    }

    protected void readOnlyChanged() {
        enabledChanged();
    }

    public Object getSelectableItems() {
        return selectableItems;
    }

    public JComponent getComponent() {
        return component;
    }

    public final void setSelectableItems(Object selectableItems) {
        Assert.notNull(selectableItems);
        if (!selectableItems.equals(this.selectableItems)) {
            this.selectableItems = selectableItems;
            selectableItemsChanged();
        }
    }

    public final void setComparator(Comparator comparator) {
        if (comparator != this.comparator || (comparator != null && !comparator.equals(this.comparator))) {
            if (this.comparator instanceof Observable) {
                ((Observable) this.comparator).deleteObserver(comparatorObserver);
            }
            this.comparator = comparator;
            if (comparator instanceof Observable) {
                ((Observable) this.comparator).addObserver(comparatorObserver);
            }
            comparatorChanged();
        }
    }

    public final void setFilter(Constraint filter) {
        if (filter != this.filter || (filter != null && !filter.equals(this.filter))) {
            if (this.filter instanceof Observable) {
                ((Observable) this.filter).deleteObserver(filterObserver);
            }
            this.filter = filter;
            if (filter instanceof Observable) {
                ((Observable) this.filter).addObserver(filterObserver);
            }
            filterChanged();
        }
    }

    protected final JComponent doBindControl() {
        doBindControl(getBindingModel());
        return getComponent();
    }

    protected abstract void doBindControl(ListModel bindingModel);

    protected void filterChanged() {
        filterConstraint.filterChanged();
    }

    protected void comparatorChanged() {
        bindingComparator.comparatorChanged();
    }

    protected void selectableItemsChanged() {
        if (filteredModel != null) {
            filteredModel.setFilteredModel(createModel());
        }
    }

    protected ListModel createModel() {
        return (ListModel) visitor.invokeVisit(selectableItemsVisitor, selectableItems);
    }

    protected AbstractFilteredListModel getFilteredModel() {
        if (filteredModel == null) {
            filteredModel = createFilteredModel(createModel(), filterConstraint);
        }
        return filteredModel;
    }

    protected ListModel getBindingModel() {
        if (bindingModel == null) {
            bindingModel = createSortedListModel(getFilteredModel(), bindingComparator);
        }
        return bindingModel;
    }

    protected ListModel createSortedListModel(ListModel listModel, Comparator comparator) {
        return new SortedListModel(listModel, comparator);
    }

    protected AbstractFilteredListModel createFilteredModel(ListModel model, Constraint constraint) {
        return new FilteredListModel(model, constraint);
    }

    protected class SelectableItemsVisitor {

        ListModel visit(ListModel listModel) {
            return listModel;
        }

        ListModel visit(ValueModel valueModel) {
            Assert.notNull(valueModel.getValue(),
                    "value of ValueModel must not be null. Use an empty Collection or Array");
            ListModel model = (ListModel) visitor.invokeVisit(this, valueModel.getValue());
            return new ValueModelFilteredListModel(model, valueModel);
        }

        ListModel visit(List list) {
            return new ListListModel(list);
        }

        ListModel visit(Collection collection) {
            return visit(new ArrayList(collection));
        }

        ListModel visit(Object[] array) {
            return visit(Arrays.asList(array));
        }

        ListModel visit(Object object) {
            return visit(new Object[] { object });
        }

        ListModel visitNull() {
            return getDefaultModel();
        }
    }

    protected class ValueModelFilteredListModel extends AbstractFilteredListModel implements PropertyChangeListener {

        private final ValueModel valueModel;

        public ValueModelFilteredListModel(ListModel model, ValueModel valueModel) {
            super(model);
            this.valueModel = valueModel;
            valueModel.addValueChangeListener(this);
        }

        public void propertyChange(PropertyChangeEvent evt) {
            setFilteredModel((ListModel) visitor.invokeVisit(selectableItemsVisitor, valueModel.getValue()));
        }

    }

    public Comparator getComparator() {
        return comparator;
    }

    protected abstract ListModel getDefaultModel();

    public Constraint getFilter() {
        return filter;
    }

    private class FilterConstraint extends Observable implements Constraint {

        public boolean test(Object argument) {
            if (filter != null) {
                return filter.test(argument);
            }
            return true;
        }

        public void filterChanged() {
            setChanged();
            notifyObservers();
        }
    }

    private class FilterObserver implements Observer {

        public void update(Observable o, Object arg) {
            filterChanged();
        }
    }

    private class BindingComparator extends Observable implements Comparator {
        public int compare(Object o1, Object o2) {
            if (comparator != null) {
                return comparator.compare(o1, o2);
            }
            return 0;
        }

        public void comparatorChanged() {
            setChanged();
            notifyObservers();
        }

    }

    private class ComparatorObserver implements Observer {

        public void update(Observable o, Object arg) {
            comparatorChanged();
        }

    }
}