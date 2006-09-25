package org.springframework.richclient.exceptionhandling;

import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;
import org.hibernate.validator.InvalidStateException;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.awt.BorderLayout;

/**
 * Displays the validation errors to the user.
 *
 * @author Geoffrey De Smet
 */
public class HibernateValidatorDialogExceptionHandler extends AbstractDialogExceptionHandler {

    private static final String CAPTION_KEY = "hibernateValidatorDialogExceptionHandler.caption";
    private static final String EXPLANATION_KEY = "hibernateValidatorDialogExceptionHandler.explanation";

    public String resolveExceptionCaption(Throwable throwable) {
        return messageSourceAccessor.getMessage(CAPTION_KEY, CAPTION_KEY);
    }

    public Object createExceptionContent(Throwable throwable) {
        if (!(throwable instanceof InvalidStateException)) {
            String ILLEGAL_THROWABLE_ARGUMENT
                    = "Could not handle exception: throwable is not an InvalidStateException:\n"
                    + throwable.getClass().getName();
            logger.error(ILLEGAL_THROWABLE_ARGUMENT);
            return ILLEGAL_THROWABLE_ARGUMENT;
        }
        InvalidStateException invalidStateException = (InvalidStateException) throwable;
        String explanation = messageSourceAccessor.getMessage(EXPLANATION_KEY, EXPLANATION_KEY);
        JPanel panel = new JPanel(new BorderLayout());
        JLabel explanationLabel = new JLabel(explanation);
        panel.add(explanationLabel, BorderLayout.NORTH);
        JList invalidValuesJList = new JList(invalidStateException.getInvalidValues());
        JScrollPane invalidValuesScrollPane = new JScrollPane(invalidValuesJList,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(invalidValuesScrollPane, BorderLayout.CENTER);
        return panel;
    }

}