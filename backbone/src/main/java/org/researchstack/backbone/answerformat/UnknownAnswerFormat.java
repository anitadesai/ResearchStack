package org.researchstack.backbone.answerformat;


@Deprecated
public class UnknownAnswerFormat extends AnswerFormat
{
    public UnknownAnswerFormat()
    {
    }

    @Override
    public QuestionType getQuestionType()
    {
        return Type.None;
    }

    @Override
    public Type getQuestionTypeEnum()
    {
        return Type.None;
    }

}
