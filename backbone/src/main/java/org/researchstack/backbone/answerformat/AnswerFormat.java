package org.researchstack.backbone.answerformat;

import org.researchstack.backbone.ui.step.body.DateQuestionBody;
import org.researchstack.backbone.ui.step.body.FormBody;
import org.researchstack.backbone.ui.step.body.IntegerPickerQuestionBody;
import org.researchstack.backbone.ui.step.body.IntegerQuestionBody;
import org.researchstack.backbone.ui.step.body.MultiChoiceQuestionBody;
import org.researchstack.backbone.ui.step.body.NotImplementedStepBody;
import org.researchstack.backbone.ui.step.body.SingleChoiceQuestionBody;
import org.researchstack.backbone.ui.step.body.SliderStepBody;
import org.researchstack.backbone.ui.step.body.TextQuestionBody;

import java.io.Serializable;

/**
 * The AnswerFormat class is the abstract base class for classes that describe the format in which a
 * survey question should be answered. The ResearchStack framework uses {@link
 * org.researchstack.backbone.step.QuestionStep} to represent questions to ask the user. Each
 * question must have an associated answer format.
 * <p>
 * To use an answer format, instantiate the appropriate answer format subclass and attach it to a
 * question step or form item. Incorporate the resulting step into a task, and present the task with
 * a {@link org.researchstack.backbone.ui.ViewTaskActivity}.
 */
public abstract class AnswerFormat implements Serializable
{
    /**
     * Default constructor. The appropriate subclass of AnswerFormat should be used instead of this
     * directly.
     */
    public AnswerFormat()
    {
    }

    /**
     * Returns the QuestionType for this answer format. Implement this in your subclass.
     *
     * @return the question type
     */
    public QuestionType getQuestionType()
    {
        return Type.None;
    }

    /**
     * Returns the Type for this answer format. Implement this in your subclass.
     *
     * @return the question type
     */
    public Type getQuestionTypeEnum() {
        return Type.None;
    }

    public static AnswerFormat fromOrdinal(int ordinal) {
        AnswerFormat answerFormat = null;
        Type type = Type.values()[ordinal];
        switch (type) {
            case SingleChoice:
                answerFormat = new ChoiceAnswerFormat(AnswerFormat.ChoiceAnswerStyle.SingleChoice);
                break;
            case MultipleChoice:
                answerFormat = new ChoiceAnswerFormat(ChoiceAnswerStyle.MultipleChoice);
                break;
            case Decimal:
                answerFormat = new DecimalAnswerFormat(0, 0);
                break;
            case Integer:
                answerFormat = new IntegerAnswerFormat(0, 0);
                break;
            case IntegerPicker:
                answerFormat = new IntegerPickerAnswerFormat(0, 0);
                break;
            case Boolean:
                answerFormat = new BooleanAnswerFormat("", "");
                break;
            case Text:
                answerFormat = new TextAnswerFormat();
                break;
            case Date:
                answerFormat = new DateAnswerFormat(AnswerFormat.DateAnswerStyle.Date);
                break;
            case Form:
                answerFormat = new FormAnswerFormat();
                break;
            case Slider:
                answerFormat = new SliderAnswerFormat(0, 0);
                break;
        }
        return answerFormat;
    }

    /**
     * Interface that {@link Type} implements. Since you cannot add a value to an existing enum, you
     * may implement this interface instead to provide your own QuestionType that provides a {@link
     * org.researchstack.backbone.ui.step.body.StepBody} class.
     */
    public interface QuestionType
    {
        Class<?> getStepBodyClass();
    }

    /**
     * The type of question. (read-only)
     * <p>
     * The type provides a default {@link org.researchstack.backbone.ui.step.body.StepBody} for that
     * type of question. A custom StepLayout implementation may provide it's own StepBody rather
     * than using the default provided by this AnswerFormat.
     */
    public enum Type implements QuestionType
    {
        // new values MUST be added to the end of the list as existing code depends
        // upon the ordinal value
        None(NotImplementedStepBody.class),
        Scale(NotImplementedStepBody.class),
        SingleChoice(SingleChoiceQuestionBody.class),
        MultipleChoice(MultiChoiceQuestionBody.class),
        Decimal(NotImplementedStepBody.class),
        Integer(IntegerQuestionBody.class),
        Boolean(SingleChoiceQuestionBody.class),
        Eligibility(NotImplementedStepBody.class),
        Text(TextQuestionBody.class),
        TimeOfDay(NotImplementedStepBody.class),
        DateAndTime(NotImplementedStepBody.class),
        Date(DateQuestionBody.class),
        TimeInterval(NotImplementedStepBody.class),
        Location(NotImplementedStepBody.class),
        Form(FormBody.class),
        Slider(SliderStepBody.class),
        IntegerPicker(IntegerPickerQuestionBody.class);

        private Class<?> stepBodyClass;

        Type(Class<?> stepBodyClass)
        {
            this.stepBodyClass = stepBodyClass;
        }

        @Override
        public Class<?> getStepBodyClass()
        {
            return stepBodyClass;
        }
    }

    /**
     * The style of the question (that is, single or multiple choice).
     */
    public enum ChoiceAnswerStyle
    {
        SingleChoice,
        MultipleChoice
    }

    /**
     * An enumeration of the format styles available for scale answers.
     */
    public enum NumberFormattingStyle
    {
        Default,
        Percent
    }

    /**
     * The style of date picker to use in an {@link DateAnswerFormat} object.
     */
    public enum DateAnswerStyle
    {
        DateAndTime,
        Date
    }
}
