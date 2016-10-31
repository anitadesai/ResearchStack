package org.researchstack.skin.task;

import android.content.Context;

import org.researchstack.backbone.answerformat.AnswerFormat;
import org.researchstack.backbone.answerformat.BooleanAnswerFormat;
import org.researchstack.backbone.answerformat.ChoiceAnswerFormat;
import org.researchstack.backbone.answerformat.DateAnswerFormat;
import org.researchstack.backbone.answerformat.IntegerAnswerFormat;
import org.researchstack.backbone.answerformat.SliderAnswerFormat;
import org.researchstack.backbone.answerformat.TextAnswerFormat;
import org.researchstack.backbone.answerformat.UnknownAnswerFormat;
import org.researchstack.backbone.model.Choice;
import org.researchstack.backbone.result.StepResult;
import org.researchstack.backbone.result.TaskResult;
import org.researchstack.backbone.step.InstructionStep;
import org.researchstack.backbone.step.QuestionStep;
import org.researchstack.backbone.step.Step;
import org.researchstack.backbone.task.Task;
import org.researchstack.backbone.utils.LogExt;
import org.researchstack.backbone.utils.TextUtils;
import org.researchstack.skin.R;
import org.researchstack.skin.model.TaskModel;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This Task allows creation of a special survey from json that has custom navigation logic.
 * <p>
 * Based on the user's answers to questions, they may be taken to a specific step rather than the
 * next one in the task.
 */
public class SmartSurveyTask extends Task implements Serializable {

    private static final String OPERATOR_SKIP = "de";
    private static final String OPERATOR_EQUAL = "eq";
    private static final String OPERATOR_NOT_EQUAL = "ne";
    private static final String OPERATOR_LESS_THAN = "lt";
    private static final String OPERATOR_GREATER_THAN = "gt";
    private static final String OPERATOR_LESS_THAN_EQUAL = "le";
    private static final String OPERATOR_GREATER_THAN_EQUAL = "ge";
    private static final String OPERATOR_OTHER_THAN = "ot";
    private static final String OPERATOR_SUM_GT = "sum_gt";

    // use this as the 'skipTo' identifier to end the survey instead of going to a question
    public static final String END_OF_SURVEY_MARKER = "END_OF_SURVEY";

    private HashMap<String, Step> steps;
    private HashMap<String, List<TaskModel.RuleModel>> rules;

    private List<String> staticStepIdentifiers;
    private List<String> dynamicStepIdentifiers;

    /**
     * Creates a SmartSurveyTask from a {@link TaskModel} object
     *
     * @param context   context for fetching any resources needed
     * @param taskModel Java representation of the task json
     */
    public SmartSurveyTask(Context context, TaskModel taskModel) {
        super(taskModel.identifier);
        steps = new HashMap<>(taskModel.elements.size());
        rules = new HashMap<>();
        staticStepIdentifiers = new ArrayList<>(taskModel.elements.size());
        for (TaskModel.StepModel stepModel : taskModel.elements) {

            if (stepModel.type.equals("SurveyQuestion")) {
                AnswerFormat answerFormat = from(context, stepModel.constraints);

                QuestionStep questionStep = new QuestionStep(stepModel.identifier,
                        stepModel.prompt,
                        answerFormat);
                questionStep.setText(stepModel.promptDetail);
                questionStep.setOptional(stepModel.optional);
                steps.put(stepModel.identifier, questionStep);
                staticStepIdentifiers.add(stepModel.identifier);
                rules.put(stepModel.identifier, stepModel.constraints.rules);

            } else if (stepModel.type.equals("InstructionStep")) {
                LogExt.i(getClass(), "Loaded an IntructionStep in SmartSurveyTask");
                InstructionStep instructionStep = new InstructionStep(stepModel.identifier,
                        stepModel.prompt,
                        stepModel.promptDetail);
                steps.put(stepModel.identifier, instructionStep);
                staticStepIdentifiers.add(stepModel.identifier);
                if (stepModel.constraints != null) {
                    rules.put(stepModel.identifier, stepModel.constraints.rules);
                }

            } else if (stepModel.type.equals("CustomStep")){
                LogExt.i(getClass(), "Loading custom step type");
                try {
                    Class stepClass = Class.forName(stepModel.stepClass);
                    LogExt.i(getClass(), "Step class " + stepClass.getName());
                    Step step = (Step) stepClass.getConstructor(String.class, String.class)
                            .newInstance(stepModel.identifier, stepModel.prompt);

                    if (! TextUtils.isEmpty(stepModel.promptDetail)) {
                        step.setText(stepModel.promptDetail);
                    }

                    if (stepModel.constraints != null) {
                        if (stepModel.constraints.customStepConstraints != null) {
                            for (String customConstraint : stepModel.constraints.customStepConstraints.keySet()) {
                                LogExt.i(getClass(), "Got value " + stepModel.constraints.customStepConstraints.get(customConstraint) +
                                        " for key " + customConstraint);
                            }
                            LogExt.i(getClass(), "Deserialized custom constraints dictionary of size " + stepModel.constraints.customStepConstraints.size());
                            step.setCustomConstraints(stepModel.constraints.customStepConstraints);
                        } else {
                            LogExt.i(getClass(), "Does not contain custom constraints");
                        }
                    }

                    steps.put(stepModel.identifier, step);
                    staticStepIdentifiers.add(stepModel.identifier);

                } catch (ClassNotFoundException e) {
                    LogExt.i(getClass(), "Step class could not be found");

                } catch (NoSuchMethodException e) {
                    LogExt.i(getClass(), "Could not find constructor for this step class");

                } catch (IllegalAccessException e) {
                    LogExt.i(getClass(), "Could not access constructor for this step class");

                } catch (InstantiationException e) {
                    LogExt.i(getClass(), "Could not instantiate step");

                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }

                //throw new UnsupportedOperationException("Unsupported step type for SmartSurvey.");
            } else {
                LogExt.e(getClass(), "Unknown step type");
            }
        }

        dynamicStepIdentifiers = new ArrayList<>(staticStepIdentifiers);
    }

    private AnswerFormat from(Context context, TaskModel.ConstraintsModel constraints) {
        AnswerFormat answerFormat;
        String type = constraints.type;
        if (type.equals("BooleanConstraints")) {
            answerFormat = new BooleanAnswerFormat(context.getString(R.string.rsb_yes),
                    context.getString(R.string.rsb_no));
        } else if (type.equals("MultiValueConstraints")) {
            LogExt.i(getClass(), "Loading step with MultiValueConstraints");
            AnswerFormat.ChoiceAnswerStyle answerStyle = constraints.allowMultiple
                    ? AnswerFormat.ChoiceAnswerStyle.MultipleChoice
                    : AnswerFormat.ChoiceAnswerStyle.SingleChoice;
            answerFormat = new ChoiceAnswerFormat(answerStyle, from(constraints.enumeration));
        } else if (type.equals("IntegerConstraints")) {
            answerFormat = new IntegerAnswerFormat(constraints.minValue, constraints.maxValue);
        } else if (type.equals("TextConstraints") || type.equals("StringConstraints")) {
            answerFormat = new TextAnswerFormat();
            boolean multipleLines = constraints.multipleLines;
            ((TextAnswerFormat) answerFormat).setIsMultipleLines(multipleLines);
        } else if (type.equals("DateConstraints")) {
            answerFormat = new DateAnswerFormat(AnswerFormat.DateAnswerStyle.Date);
        } else if (type.equals("SliderConstraints")) {
            answerFormat = new SliderAnswerFormat(constraints.minValue, constraints.maxValue);

            if (constraints.maxValueDescription != null && !constraints.maxValueDescription.isEmpty()) {
                ((SliderAnswerFormat) answerFormat).setMaxText(constraints.maxValueDescription);
            }

            if (constraints.minValueDescription != null && !constraints.minValueDescription.isEmpty()) {
                ((SliderAnswerFormat) answerFormat).setMinText(constraints.minValueDescription);
            }

            if (constraints.maxValueImage != null && !constraints.maxValueImage.isEmpty()) {
                //((SliderAnswerFormat) answerFormat).setMaxImage(resources.getDrawable(resources.getIdentifier(constraints.maxValueImage, "drawable",
                //      context.getPackageName())));
            }

            if (constraints.minValueImage != null && !constraints.minValueImage.isEmpty()) {
                // ((SliderAnswerFormat) answerFormat).setMinImage(resources.getDrawable(resources.getIdentifier(constraints.minValueImage, "drawable",
                //       context.getPackageName())));
            }

            if (constraints.color != null && !constraints.color.isEmpty()) {
                ((SliderAnswerFormat) answerFormat).setColor(constraints.color);
            }

            if (constraints.sliderView != null && !constraints.sliderView.isEmpty()) {
                ((SliderAnswerFormat) answerFormat).setSliderView(constraints.sliderView);
            }

            ((SliderAnswerFormat) answerFormat).setShowVal(constraints.showValue);
        } else {
            answerFormat = new UnknownAnswerFormat();
        }
        return answerFormat;
    }

    private Choice[] from(List<TaskModel.EnumerationModel> enumeration) {
        LogExt.i(getClass(), "instantiating step choices from enumeration model");
        Choice[] choices = new Choice[enumeration.size()];

        for (int i = 0; i < enumeration.size(); i++) {
            TaskModel.EnumerationModel choice = enumeration.get(i);
            if (choice.value instanceof String) {
                choices[i] = new Choice<>(choice.label, (String) choice.value);

                if (choice.exclusive != null) {
                    choices[i].setExclusive(choice.exclusive);
                }
            } else if (choice.value instanceof Number) {
                // if the field type is Object, gson turns all numbers into doubles. Assuming Integer
                choices[i] = new Choice<>(choice.label, ((Number) choice.value).intValue());

                if (choice.exclusive != null) {
                    choices[i].setExclusive(choice.exclusive);
                }

            } else {
                throw new RuntimeException(
                        "String and Integer are the only supported values for generating Choices from json");
            }
        }
        return choices;
    }

    /**
     * Returns the list of steps for the task.
     * @return a collection containing the steps of this task
     */
    public Collection<Step> getSteps() {
        return steps.values();
    }

    /**
     * Returns the next step in the task based on current answers, or null if at the end.
     * <p>
     * This method rebuilds the order of the steps based on the current results and returns the next
     * one.
     *
     * @param step   The reference step. Pass null to specify the first step.
     * @param result A snapshot of the current set of results.
     * @return the Step to navigate to
     */
    @Override
    public Step getStepAfterStep(Step step, TaskResult result) {
        String currentIdentifier = step == null ? null : step.getIdentifier();
        refillDynamicStepIdentifiers(currentIdentifier);

        String skipToStep = null;

        List<TaskModel.RuleModel> stepRules = rules.get(currentIdentifier);
        if (stepRules != null && !stepRules.isEmpty()) {
            LogExt.d(getClass(), "Rules exist for this step");
            StepResult stepResult = result.getStepResult(currentIdentifier);
            Object answer = null;
            if (stepResult != null) {
                answer = stepResult.getResult();
            }
            skipToStep = processRules(stepRules, answer, result);

            if (skipToStep != null && skipToStep.equals(END_OF_SURVEY_MARKER)) {
                return null;
            }

            if (skipToStep != null) {
                adjustDynamicStepIdentifiers(skipToStep, currentIdentifier);
            }
        } else {
            LogExt.d(getClass(), "No rules for this step");
        }

        String nextStepIdentifier = nextStepIdentifier(true, currentIdentifier);

        return nextStepIdentifier == null ? null : steps.get(nextStepIdentifier);
    }

    /**
     * Returns the step that should be before the current step based on current results.
     * <p>
     * This method rebuilds the order of the remaining steps based on the current results and
     * returns the previous one to the current step.
     *
     * @param step   The reference step. Pass null to specify the last step.
     * @param result A snapshot of the current set of results.
     * @return the Step to navigate to
     */
    @Override
    public Step getStepBeforeStep(Step step, TaskResult result) {
        String currentIdentifier = step == null ? null : step.getIdentifier();
        refillDynamicStepIdentifiers(currentIdentifier);
        String previousStepIdentifier = nextStepIdentifier(false, currentIdentifier);
        return previousStepIdentifier == null ? null : steps.get(previousStepIdentifier);
    }

    @Override
    public Step getStepWithIdentifier(String identifier) {
        return steps.get(identifier);
    }

    /**
     * Returns the current progress String for use in the action bar
     * <p>
     * This is updated based on the current and total in the dynamic list of steps.
     *
     * @param context for fetching resources
     * @param step    the current step
     * @return
     */
    @Override
    public String getTitleForStep(Context context, Step step) {
        int currentIndex = staticStepIdentifiers.indexOf(step.getIdentifier()) + 1;
        return context.getString(R.string.rsb_format_step_title,
                currentIndex,
                staticStepIdentifiers.size());
    }

    @Override
    public TaskProgress getProgressOfCurrentStep(Step step, TaskResult result) {
        int current = staticStepIdentifiers.indexOf(step == null ? -1 : step.getIdentifier());
        return new TaskProgress(current, staticStepIdentifiers.size());
    }

    @Override
    public void validateParameters() {
        // Construction validates most issues, add some validation here if needed
    }

    private String nextStepIdentifier(boolean after, String currentIdentifier) {
        if (currentIdentifier == null && after) {
            return !dynamicStepIdentifiers.isEmpty() ? dynamicStepIdentifiers.get(0) : null;
        }

        int currentIndex = dynamicStepIdentifiers.indexOf(currentIdentifier);
        int newIndex = -1;

        if (after) {
            if (currentIndex + 1 < dynamicStepIdentifiers.size()) {
                newIndex = currentIndex + 1;
            }
        } else {
            if (currentIndex >= 1) {
                newIndex = currentIndex - 1;
            }
        }

        return newIndex != -1 ? dynamicStepIdentifiers.get(newIndex) : null;
    }

    private void refillDynamicStepIdentifiers(String currentIdentifier) {
        //Remove till end in dynamic
        int currentIndexInDynamic = dynamicStepIdentifiers.indexOf(currentIdentifier);
        currentIndexInDynamic = currentIndexInDynamic == -1 ? 0 : currentIndexInDynamic;
        dynamicStepIdentifiers = new ArrayList<>(dynamicStepIdentifiers.subList(0, currentIndexInDynamic));

        //Add array from static
        int currentIndexInStatic = staticStepIdentifiers.indexOf(currentIdentifier);
        currentIndexInStatic = currentIndexInStatic == -1 ? 0 : currentIndexInStatic;

        dynamicStepIdentifiers.addAll(staticStepIdentifiers.subList(currentIndexInStatic,
                staticStepIdentifiers.size()));
    }

    private void adjustDynamicStepIdentifiers(String skipToIdentifier, String currentIdentifier) {
        int currentIndex = dynamicStepIdentifiers.indexOf(currentIdentifier);
        int skipToIndex = dynamicStepIdentifiers.indexOf(skipToIdentifier);

        if (currentIndex == -1 || skipToIndex == -1) {
            return;
        }

        if (skipToIndex > currentIndex) {
            while (!dynamicStepIdentifiers.get(currentIndex + 1).equals(skipToIdentifier)) {
                dynamicStepIdentifiers.remove(currentIndex + 1);
            }
        }
    }

    private String processRules(List<TaskModel.RuleModel> stepRules, Object answer, TaskResult result) {
        String skipToIdentifier = null;

        for (TaskModel.RuleModel stepRule : stepRules) {
            skipToIdentifier = checkRule(stepRule, answer, result);
            if (skipToIdentifier != null) {
                break;
            }
        }

        return skipToIdentifier;
    }

    private String checkRule(TaskModel.RuleModel stepRule, Object answer, TaskResult result) {
        String operator = stepRule.operator;
        String skipTo = stepRule.skipTo;
        Object value = stepRule.value;

        if (operator.equals(OPERATOR_SKIP)) {
            return skipTo;
        } else if (answer instanceof Integer) {
            return checkNumberRule(operator, skipTo, ((Number) value).intValue(), (Integer) answer, result);
        } else if (answer instanceof Double) {
            return checkNumberRule(operator, skipTo, ((Number) value).doubleValue(), (Double) answer, result);
        } else if (answer instanceof Boolean) {
            Boolean booleanValue;

            if (value instanceof Boolean) {
                booleanValue = (Boolean) value;
            } else if (value instanceof Number) {
                booleanValue = ((Number) value).intValue() == 0 ? Boolean.FALSE : Boolean.TRUE;
            } else if (value instanceof String) {
                booleanValue = Boolean.valueOf((String) value);
            } else {
                throw new RuntimeException("Invalid value for Boolean skip rule");
            }

            return checkEqualsRule(operator, skipTo, booleanValue, answer);
        } else if (answer instanceof String) {
            return checkEqualsRule(operator, skipTo, value, answer);
        } else {
            LogExt.e(getClass(), "Unsupported answer type for smart survey rules");
        }

        return null;
    }

    private <T> String checkEqualsRule(String operator, String skipTo, T value, T answer) {
        switch (operator) {
            case OPERATOR_EQUAL:
                return value.equals(answer) ? skipTo : null;
            case OPERATOR_NOT_EQUAL:
                return !value.equals(answer) ? skipTo : null;
        }
        return null;
    }

    private <T extends Comparable<T>> String checkNumberRule(String operator, String skipTo, T value, T answer, TaskResult result) {
        int compare = 0;
        if (OPERATOR_SUM_GT.equalsIgnoreCase(operator)) {
            // This code is really a hack to meet a specific use case for Cancer Distress Coach.
            // Rule navigation needs to be greatly expanded before being committed back to
            // Research Stack.
            Map<String, StepResult> results = result.getResults();
            List<String> identifiers = new ArrayList<>();
            identifiers.addAll(results.keySet());
            Collections.sort(identifiers); // sorting is not really a valid way to organize the results

            Double sum = 0.;
            for (String identifier : identifiers) {
                StepResult stepResult = result.getStepResult(identifier);
                if (stepResult != null) {
                    Object stepAnswer = null;
                    stepAnswer = stepResult.getResult();
                    if (stepAnswer instanceof Integer) {
                        sum += (Integer) stepAnswer;
                    } else if (stepAnswer instanceof Double) {
                        sum += (Double) stepAnswer;
                    }
                }
            }

            operator = OPERATOR_GREATER_THAN;
            if (answer instanceof Integer) {
                answer = (T) new Integer(sum.intValue());
            } else if (answer instanceof Double) {
                answer = (T) sum;
            }
        }
        compare = answer.compareTo(value);

        switch (operator) {
            case OPERATOR_EQUAL:
                return compare == 0 ? skipTo : null;
            case OPERATOR_NOT_EQUAL:
                return compare != 0 ? skipTo : null;
            case OPERATOR_GREATER_THAN:
                return compare > 0 ? skipTo : null;
            case OPERATOR_GREATER_THAN_EQUAL:
                return compare >= 0 ? skipTo : null;
            case OPERATOR_LESS_THAN:
                return compare < 0 ? skipTo : null;
            case OPERATOR_LESS_THAN_EQUAL:
                return compare <= 0 ? skipTo : null;
        }

        return null;

    }
}
