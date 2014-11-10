package org.iplantc.workflow.experiment;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.iplantc.workflow.WorkflowException;
import org.iplantc.workflow.core.TransformationActivity;
import org.iplantc.workflow.dao.DaoFactory;
import org.iplantc.workflow.model.Template;
import org.iplantc.persistence.dto.transformation.Transformation;

import net.sf.json.JSONObject;
import org.iplantc.persistence.dto.components.DeployedComponent;
import org.iplantc.persistence.dto.step.TransformationStep;
import org.iplantc.workflow.user.UserDetails;

/**
 * A factory used to create job request formatters.
 *
 * @author Dennis Roberts
 */
public class JobRequestFormatterFactory {

    /**
     * Used to create data access objects.
     */
    private final DaoFactory daoFactory;

    /**
     * Used to create URLs to be used by the jobs.
     */
    private final UrlAssembler urlAssembler;

    /**
     * The details of the user who submitted the job.
     */
    private final UserDetails userDetails;

    /**
     * The path to the home directory in iRODS.
     */
    private final String irodsHome;

    /**
     * @param daoFactory used to create data access objects.
     * @param urlAssembler used to create URLs that will be used by the jobs.
     * @param userDetails information about the user who submitted the jobs.
     * @param irodsHome the path to the home directory in iRODS.
     */
    public JobRequestFormatterFactory(DaoFactory daoFactory, UrlAssembler urlAssembler,
            UserDetails userDetails, String irodsHome) {
        this.daoFactory = daoFactory;
        this.urlAssembler = urlAssembler;
        this.userDetails = userDetails;
        this.irodsHome = irodsHome;
    }

    /**
     * Gets the appropriate job request formatter factory for the given experiment.
     *
     * @param experiment the experiment.
     * @return the job request formatter.
     * @throws WorkflowException if the appropriate formatter can't be determined.
     */
    public JobRequestFormatter getFormatter(JSONObject experiment) {
        String componentType = determineComponentType(experiment);
        JobRequestFormatter formatter = null;
        if (StringUtils.equals(componentType, "executable")) {
            formatter = new CondorJobRequestFormatter(daoFactory, urlAssembler, userDetails, experiment);
        }
        else if (StringUtils.equals(componentType, "fAPI")) {
            formatter = new FapiJobRequestFormatter(daoFactory, userDetails, experiment, irodsHome);
        }
        else {
            throw new WorkflowException("unrecognized component type: " + componentType);
        }
        return formatter;
    }

    /**
     * Determines the type of the first deployed component in the analysis.
     *
     * @param experiment the experiment being submitted.
     * @return the deployed component type.
     */
    private String determineComponentType(JSONObject experiment) {
        String analysisId = experiment.getString("analysis_id");
        int startingStep = experiment.optInt("starting_step", 1) - 1;
        TransformationActivity analysis = findAnalysis(analysisId);
        Template template = findTemplate(analysis, startingStep);
        DeployedComponent component = findDeployedComponent(template);
        return component.getType();
    }

    /**
     * Finds the deployed component for the given template, throwing an exception if the deployed component can't be
     * found.
     *
     * @param template the template.
     * @return the deployed component.
     * @throws WorkflowException if the deployed component can't be found.
     */
    private DeployedComponent findDeployedComponent(Template template) {
        DeployedComponent component = findDeployedComponentForTemplate(template);
        if (component == null) {
            throw new WorkflowException("no deployed component found for template, " + template.getId());
        }
        return component;
    }

    /**
     * Finds the deployed component for the given template, returning null if the deployed component can't be found.
     *
     * @param template the template.
     * @return the deployed component or null if the deployed component can't be found.
     */
    private DeployedComponent findDeployedComponentForTemplate(Template template) {
        DeployedComponent component = null;
        String componentId = template.getComponent();
        if (componentId != null) {
            component = daoFactory.getDeployedComponentDao().findById(componentId);
        }
        return component;
    }

    /**
     * Finds the template for the given analysis, throwing an exception if the template can't be found.
     *
     * @param analysis the analysis.
     * @param stepNumber the index of the step to check.
     * @return the template.
     * @throws WorkflowException if the template can't be found.
     */
    private Template findTemplate(TransformationActivity analysis, int stepNumber) {
        Template template = findTemplateForAnalysisStep(analysis, stepNumber);
        if (template == null) {
            String msg = "no template found for analysis, " + analysis.getId() + ", step number, " + stepNumber;
            throw new WorkflowException(msg);
        }
        return template;
    }

    /**
     * Finds the template for the given analysis, returning null if the template can't be found.
     *
     * @param analysis the analysis.
     * @param stepNumber the index of the step to check.
     * @return the template or null if the template can't be found.
     */
    private Template findTemplateForAnalysisStep(TransformationActivity analysis, int stepNumber) {
        Template template = null;
        List<TransformationStep> steps = analysis.getSteps();
        if (steps != null && !steps.isEmpty()) {
            TransformationStep step = steps.get(stepNumber);
            if (step != null) {
                template = findTemplateForTransformationStep(step);
            }
        }
        return template;
    }

    /**
     * Finds the template for the given transformation step, returning null if the template can't be found.
     *
     * @param step the transformation step.
     * @return the template or null if the template can't be found.
     */
    private Template findTemplateForTransformationStep(TransformationStep step) {
        Template template = null;
        Transformation transformation = step.getTransformation();
        if (transformation != null) {
            String templateId = transformation.getTemplate_id();
            if (templateId != null) {
                template = daoFactory.getTemplateDao().findById(templateId);
            }
        }
        return template;
    }

    /**
     * Finds the analysis with the given analysis ID.
     *
     * @param analysisId the analysis ID.
     * @return the analysis.
     * @throws WorkflowException if the analysis can't be found.
     */
    private TransformationActivity findAnalysis(String analysisId) {
        TransformationActivity analysis = daoFactory.getTransformationActivityDao().findById(analysisId);
        if (analysis == null) {
            throw new WorkflowException("analysis, " + analysisId + ", not found");
        }
        return analysis;
    }
}