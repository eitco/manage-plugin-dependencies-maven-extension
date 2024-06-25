package de.eitco.cicd.manage.plugin.dependencies.maven.extension;

import com.google.common.base.Strings;
import org.apache.maven.model.*;
import org.apache.maven.model.building.*;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.codehaus.plexus.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.util.*;

@Named
@Singleton
@Component(role = ModelBuilder.class)
public class ManagePluginDependenciesModelBuilder extends DefaultModelBuilder {

    final private Logger logger = LoggerFactory.getLogger(ManagePluginDependenciesModelBuilder.class);

    private ModelBuilder delegatedModelBuilder;

    private List<ModelBuilder> modelBuilders;

    private ProjectDependenciesResolver projectDependenciesResolver;

    @Inject
    void setProjectDependenciesResolver(ProjectDependenciesResolver projectDependenciesResolver) {
        this.projectDependenciesResolver = projectDependenciesResolver;
    }

    @Inject
    void setDelegatedModelBuilder(List<ModelBuilder> modelBuilders) {

        this.modelBuilders = modelBuilders;
    }

    private ModelBuilder getDelegatedModelBuilder() {

        if (delegatedModelBuilder != null) {

            return delegatedModelBuilder;
        }

        return this.delegatedModelBuilder = modelBuilders.stream()
                // Avoid circular dependency
                .filter(modelProcessor -> !Objects.equals(modelProcessor, this))
                .findFirst()
                // There is normally always at least one implementation available: org.apache.maven.model.building.DefaultModelBuilder
                .orElseThrow(() -> new NoSuchElementException("Unable to find default ModelBuilder"));

    }

    @Override
    public ModelBuildingResult build(ModelBuildingRequest request) throws ModelBuildingException {

        return adapt(getDelegatedModelBuilder().build(request), request);
    }

    @Override
    public ModelBuildingResult build(ModelBuildingRequest request, ModelBuildingResult result) throws ModelBuildingException {

        return adapt(getDelegatedModelBuilder().build(request, result), request);
    }

    @Override
    public Result<? extends Model> buildRawModel(File pomFile, int validationLevel, boolean locationTracking) {

        try {
            return adapt(getDelegatedModelBuilder().buildRawModel(pomFile, validationLevel, locationTracking));
        } catch (ModelBuildingException e) {
            throw new RuntimeException(e);
        }
    }


    public ModelBuildingResult adapt(ModelBuildingResult build, ModelBuildingRequest request) throws ModelBuildingException {

        Model effectiveModel = build.getEffectiveModel();

        try {

            adapt(effectiveModel, request);

        } catch (UnresolvableModelException e) {
            throw new RuntimeException(e);
        }

        return build;

    }

    public <ModelType extends Model> Result<ModelType> adapt(Result<ModelType> result) throws ModelBuildingException {

        try {
            adapt(result.get(), null);

        } catch (UnresolvableModelException e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    public void adapt(Model model, ModelBuildingRequest request) throws UnresolvableModelException, ModelBuildingException {


        if (model == null) {

            return;
        }

        if (model.getBuild() == null) {

            return;
        }

        List<Plugin> plugins = model.getBuild().getPlugins();

        Map<String, String> dependencyVersions = new HashMap<>();

        for (Plugin plugin : plugins) {

            adaptPlugin(plugin, dependencyVersions, model, request);
        }

        if (model.getBuild().getPluginManagement() == null) {

            return;
        }

        for (Plugin plugin : model.getBuild().getPluginManagement().getPlugins()) {

            adaptPlugin(plugin, dependencyVersions, model, request);
        }
    }

    private void adaptPlugin(Plugin plugin, Map<String, String> dependencyVersions, Model model, ModelBuildingRequest request) throws UnresolvableModelException, ModelBuildingException {

        List<Dependency> pluginDependencies = plugin.getDependencies();

        for (Dependency dependency : pluginDependencies) {

            if (dependency.getVersion() != null) {

                continue;
            }

            String dependencyKey = dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getType() + ":" + dependency.getClassifier();

            String version = dependencyVersions.get(dependencyKey);

            if (version == null) {

                version = manageDependencyVersion(model, dependency, request);
                dependencyVersions.put(version, dependencyKey);
            }

            if (!Strings.isNullOrEmpty(version)) {

                logger.info("setting version of {} to {}", dependencyKey, version);
                dependency.setVersion(version);

            } else {

                logger.warn("unmanaged plugin dependency without version found {} of plugin {}:{}:{}", dependencyKey, plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion());
            }
        }
    }

    private String manageDependencyVersion(Model model, Dependency dependency, ModelBuildingRequest request) throws UnresolvableModelException, ModelBuildingException {


        DependencyManagement dependencyManagement = model.getDependencyManagement();

        String result = manageDependencyVersion(dependencyManagement, dependency, request);

        if (result != null) {

            return result;
        }

        logger.debug("dependency management not found in {}:{}:{}", model.getGroupId(), model.getArtifactId(), model.getVersion());

        return "";
    }

    private String manageDependencyVersion(DependencyManagement dependencyManagement, Dependency dependency, ModelBuildingRequest request) throws UnresolvableModelException, ModelBuildingException {

        if (dependencyManagement == null) {

            return null;
        }

        for (Dependency managedDependency : dependencyManagement.getDependencies()) {

            logger.debug("checking dependency management for {}:{} => {}", managedDependency.getGroupId(), managedDependency.getArtifactId(), managedDependency.getVersion());

            if (
                    Objects.equals(managedDependency.getGroupId(), dependency.getGroupId()) &&
                            Objects.equals(managedDependency.getArtifactId(), dependency.getArtifactId()) &&
                            Objects.equals(managedDependency.getType(), dependency.getType()) &&
                            Objects.equals(managedDependency.getClassifier(), dependency.getClassifier())
            ) {

                return managedDependency.getVersion();
            }

            if (
                    Objects.equals(managedDependency.getScope(), "import") &&
                            Objects.equals(managedDependency.getType(), "pom")
            ) {

                logger.debug("checking dependency management import {}:{}:{}", managedDependency.getGroupId(), managedDependency.getArtifactId(), managedDependency.getVersion());

                ModelSource dependencyImport = request.getModelResolver().resolveModel(managedDependency);

                ModelBuildingRequest importRequest = new DefaultModelBuildingRequest(request);
                importRequest.setModelSource(dependencyImport);

                ModelBuildingResult imported = getDelegatedModelBuilder().build(importRequest);

                DependencyManagement importedDependencyManagement = imported.getEffectiveModel().getDependencyManagement();

                String version = manageDependencyVersion(importedDependencyManagement, dependency, request);

                if (version != null) {

                    return version;
                }
            }
        }

        return null;
    }
}
