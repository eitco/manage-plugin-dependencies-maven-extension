package de.eitco.cicd.manage.plugin.dependencies.maven.extension;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.building.DefaultModelBuilder;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.model.building.ModelCache;
import org.apache.maven.model.building.Result;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.codehaus.plexus.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import static java.util.Collections.emptyMap;
import static java.util.function.Predicate.not;

@Named
@Singleton
@Component(role = ModelBuilder.class)
public class ManagePluginDependenciesModelBuilder extends DefaultModelBuilder {
    private static final Logger logger = LoggerFactory.getLogger(ManagePluginDependenciesModelBuilder.class);
    private static final String MODEL_CACHE_KEY = "manage";

    private ModelBuilder delegatedModelBuilder;
    private List<ModelBuilder> modelBuilders;

    @Inject
    void setDelegatedModelBuilder(List<ModelBuilder> modelBuilders) {
        this.modelBuilders = modelBuilders;
    }

    private ModelBuilder getDelegatedModelBuilder() {
        if (delegatedModelBuilder == null) {
            delegatedModelBuilder = modelBuilders.stream()
                    .filter(not(this::equals)) // Avoid circular dependency
                    .findFirst()
                    // There is normally always at least one implementation available: org.apache.maven.model.building.DefaultModelBuilder
                    .orElseThrow(() -> new NoSuchElementException("Unable to find default ModelBuilder"));
        }
        return delegatedModelBuilder;
    }

    @Override
    public ModelBuildingResult build(ModelBuildingRequest request) throws ModelBuildingException {
        return adapt(getDelegatedModelBuilder().build(request), request);
    }

    @Override
    public ModelBuildingResult build(ModelBuildingRequest request,
                                     ModelBuildingResult result) throws ModelBuildingException {
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

    public ModelBuildingResult adapt(ModelBuildingResult build,
                                     ModelBuildingRequest request) throws ModelBuildingException {
        Model effectiveModel = build.getEffectiveModel();
        adapt(effectiveModel, request);
        return build;
    }

    public <ModelType extends Model> Result<ModelType> adapt(Result<ModelType> result) throws ModelBuildingException {
        if (!result.hasErrors()) {
            adapt(result.get(), null);
        }
        return result;
    }

    public void adapt(Model model, ModelBuildingRequest request) throws ModelBuildingException {
        if (model == null) {
            return;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Processing model: {}", getModelKey(model));
        }
        if (request != null) {
            modelCachePutIfAbsent(request.getModelCache(), model);
        }
        if (model.getBuild() == null) {
            return;
        }
        Map<String, String> dependencyManagementIndex = buildDependencyManagementIndex(model, request);
        List<Plugin> plugins = model.getBuild().getPlugins();
        for (Plugin plugin : plugins) {
            adaptPlugin(plugin, dependencyManagementIndex, model);
        }
        if (model.getBuild().getPluginManagement() != null) {
            for (Plugin plugin : model.getBuild().getPluginManagement().getPlugins()) {
                adaptPlugin(plugin, dependencyManagementIndex, model);
            }
        }
    }

    private static void modelCachePutIfAbsent(ModelCache modelCache, Model model) {
        if (modelCache == null || modelCache.get(model.getGroupId(), model.getArtifactId(), model.getVersion(), MODEL_CACHE_KEY) != null) {
            return;
        }
        modelCache.put(model.getGroupId(), model.getArtifactId(), model.getVersion(), MODEL_CACHE_KEY, model);
    }

    private static Model modelCacheGet(ModelCache modelCache, Dependency dependency) {
        if (modelCache == null) {
            return null;
        }
        return (Model) modelCache.get(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), MODEL_CACHE_KEY);
    }

    private static String getModelKey(Model model) {
        return "%s:%s:%s".formatted(model.getGroupId(), model.getArtifactId(), model.getVersion());
    }

    private static String getDepVersionIndexKey(Dependency dependency) {
        return "%s:%s:%s%s".formatted(dependency.getGroupId(), dependency.getArtifactId(),
                Objects.toString(dependency.getType(), "jar"),
                dependency.getClassifier() != null ? ":" + dependency.getClassifier() : "");
    }

    private static String getPluginKey(Plugin plugin) {
        return "%s:%s".formatted(plugin.getGroupId(), plugin.getArtifactId());
    }

    private Map<String, String> buildDependencyManagementIndex(Model model, ModelBuildingRequest request) throws ModelBuildingException {
        DependencyManagement dependencyManagement = model.getDependencyManagement();
        if (dependencyManagement == null) {
            return emptyMap();
        }
        Map<String, String> index = new HashMap<>();
        for (Dependency managedDependency : dependencyManagement.getDependencies()) {
            index.put(getDepVersionIndexKey(managedDependency), managedDependency.getVersion());
            if (request != null && "import".equals(managedDependency.getScope()) && "pom".equals(managedDependency.getType())) {
                DependencyManagement importedDM = getDependencyManagement(request, managedDependency);
                if (importedDM == null) {
                    continue;
                }
                for (Dependency importedDep : importedDM.getDependencies()) {
                    index.putIfAbsent(getDepVersionIndexKey(importedDep), importedDep.getVersion());
                }
            }
        }
        return index;
    }

    private void adaptPlugin(Plugin plugin, Map<String, String> dependencyManagementIndex, Model model) {
        String modelKey = getModelKey(model);
        String pluginKey = getPluginKey(plugin);
        for (Dependency dependency : plugin.getDependencies()) {
            if (dependency.getVersion() != null) {
                continue;
            }
            String dependencyKey = getDepVersionIndexKey(dependency);
            String version = dependencyManagementIndex.get(dependencyKey);
            if (version == null) {
                logger.warn("Unmanaged plugin dependency without version found {} of plugin {} in model {}", dependencyKey, pluginKey, modelKey);
                continue;
            }
            dependency.setVersion(version);
            logger.info("Set version of model {} plugin {} dependency {} to {}", modelKey, pluginKey, dependencyKey, version);
        }
    }

    private DependencyManagement getDependencyManagement(ModelBuildingRequest request, Dependency managedDependency) throws ModelBuildingException {
        Model model = modelCacheGet(request.getModelCache(), managedDependency);
        if (model != null) {
            return model.getDependencyManagement();
        }
        try {
            return resolveFromRemoteRepo(managedDependency, request);
        } catch (UnresolvableModelException ex) {
            throw new RuntimeException(ex);
        }
    }

    private DependencyManagement resolveFromRemoteRepo(Dependency managedDependency, ModelBuildingRequest request)
            throws ModelBuildingException, UnresolvableModelException {
        var dependencyImport = request.getModelResolver().resolveModel(managedDependency);
        ModelBuildingRequest importRequest = new DefaultModelBuildingRequest(request);
        importRequest.setModelSource(dependencyImport);
        ModelBuildingResult imported = getDelegatedModelBuilder().build(importRequest);
        return imported.getEffectiveModel().getDependencyManagement();
    }
}
