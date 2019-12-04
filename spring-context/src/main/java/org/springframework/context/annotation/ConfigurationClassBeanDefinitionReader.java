/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.annotation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.RequiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.parsing.Location;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.parsing.SourceExtractor;
import org.springframework.beans.factory.support.AbstractBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.StringUtils;

/**
 * Reads a given fully-populated set of ConfigurationClass instances, registering bean
 * definitions with the given {@link BeanDefinitionRegistry} based on its contents.
 *
 * <p>This class was modeled after the {@link BeanDefinitionReader} hierarchy, but does
 * not implement/extend any of its artifacts as a set of configuration classes is not a
 * {@link Resource}.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @since 3.0
 * @see ConfigurationClassParser
 * 注册配置类中的bean定义
 */
class ConfigurationClassBeanDefinitionReader {

	private static final Log logger = LogFactory.getLog(ConfigurationClassBeanDefinitionReader.class);

	private static final ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();

	private final BeanDefinitionRegistry registry;

	private final SourceExtractor sourceExtractor;

	private final ProblemReporter problemReporter;

	private final MetadataReaderFactory metadataReaderFactory;

	private final ResourceLoader resourceLoader;

	private final Environment environment;

	private final BeanNameGenerator importBeanNameGenerator;

	private final ImportRegistry importRegistry;

	private final ConditionEvaluator conditionEvaluator;


	/**
	 * Create a new {@link ConfigurationClassBeanDefinitionReader} instance that will be used
	 * to populate the given {@link BeanDefinitionRegistry}.
	 */
	ConfigurationClassBeanDefinitionReader(BeanDefinitionRegistry registry, SourceExtractor sourceExtractor,
			ProblemReporter problemReporter, MetadataReaderFactory metadataReaderFactory,
			ResourceLoader resourceLoader, Environment environment, BeanNameGenerator importBeanNameGenerator,
			ImportRegistry importRegistry) {

		this.registry = registry;
		this.sourceExtractor = sourceExtractor;
		this.problemReporter = problemReporter;
		this.metadataReaderFactory = metadataReaderFactory;
		this.resourceLoader = resourceLoader;
		this.environment = environment;
		this.importBeanNameGenerator = importBeanNameGenerator;
		this.importRegistry = importRegistry;
		this.conditionEvaluator = new ConditionEvaluator(registry, environment, resourceLoader);
	}


	/**
	 * Read {@code configurationModel}, registering bean definitions
	 * with the registry based on its contents.
	 */
	public void loadBeanDefinitions(Set<ConfigurationClass> configurationModel) {
		//很经典的评估器设计，可以很容易得替换设计
		TrackedConditionEvaluator trackedConditionEvaluator = new TrackedConditionEvaluator();

		//遍历每一个用户自定义的配置类
		for (ConfigurationClass configClass : configurationModel) {
			//从配置类中加载bean定义
			loadBeanDefinitionsForConfigurationClass(configClass/**用户自定义的配置类*/, trackedConditionEvaluator/**评估器*/);
		}
	}

	/**
	 * Read a particular {@link ConfigurationClass}, registering bean definitions
	 * for the class itself and all of its {@link Bean} methods.
	 */
	private void loadBeanDefinitionsForConfigurationClass(ConfigurationClass configClass/**用户自定义的配置类*/,
			TrackedConditionEvaluator trackedConditionEvaluator/**评估器*/) {

		//是否要跳过，跳过的话，就直接return了
		if (trackedConditionEvaluator.shouldSkip(configClass)) {
			// 用户自定义的配置类的名字
			String beanName = configClass.getBeanName();
			// 如果已经注册过了定义（containsBeanDefinition），就移除它的定义（removeBeanDefinition），因为要跳过呀
			if (StringUtils.hasLength(beanName) && this.registry.containsBeanDefinition(beanName)) {
				this.registry.removeBeanDefinition(beanName);
			}
			this.importRegistry.removeImportingClassFor(configClass.getMetadata().getClassName());
			return;
		}

		//是否是导入的
		if (configClass.isImported()) {
			registerBeanDefinitionForImportedConfigurationClass(configClass/**用户自定义的配置类*/);
		}
		for (BeanMethod beanMethod : configClass.getBeanMethods()) {
			//比如在一个method上有@Bean
			loadBeanDefinitionsForBeanMethod(beanMethod);
		}
		//loadBeanDefinitionsFromImportedResources like classpath:
		loadBeanDefinitionsFromImportedResources(configClass.getImportedResources());
		//loadBeanDefinitionsFromRegistrars like @Import
		loadBeanDefinitionsFromRegistrars(configClass.getImportBeanDefinitionRegistrars());
	}

	/**
	 * Register the {@link Configuration} class itself as a bean definition.
	 */
	private void registerBeanDefinitionForImportedConfigurationClass(ConfigurationClass configClass/**用户自定义的配置类*/) {
		//拿到注解元数据
		AnnotationMetadata metadata = configClass.getMetadata();
		AnnotatedGenericBeanDefinition configBeanDef = new AnnotatedGenericBeanDefinition(metadata);

		if (ConfigurationClassUtils.checkConfigurationClassCandidate(configBeanDef, this.metadataReaderFactory)) {
			ScopeMetadata scopeMetadata = scopeMetadataResolver.resolveScopeMetadata(configBeanDef);
			configBeanDef.setScope(scopeMetadata.getScopeName());
			String configBeanName = this.importBeanNameGenerator.generateBeanName(configBeanDef, this.registry);
			AnnotationConfigUtils.processCommonDefinitionAnnotations(configBeanDef, metadata);

			// 生成bean定义的Holder
			BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(configBeanDef, configBeanName);
			definitionHolder = AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
			//注册bean定义
			this.registry.registerBeanDefinition(definitionHolder.getBeanName(), definitionHolder.getBeanDefinition());
			configClass.setBeanName(configBeanName);
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Registered bean definition for imported @Configuration class %s", configBeanName));
			}
		}
		else {
			//报告有错误，好经典
			this.problemReporter.error(
					new InvalidConfigurationImportProblem(metadata.getClassName(), configClass.getResource(), metadata));
		}
	}

	/**
	 * Read the given {@link BeanMethod}, registering bean definitions
	 * with the BeanDefinitionRegistry based on its contents.
	 *
	 * 比如在一个method上有@Bean
	 */
	private void loadBeanDefinitionsForBeanMethod(BeanMethod beanMethod) {
		//拿到配置类
		ConfigurationClass configClass = beanMethod.getConfigurationClass();
		//拿到方法元数据
		MethodMetadata metadata = beanMethod.getMetadata();
		//拿到方法名
		String methodName = metadata.getMethodName();

		// Do we need to mark the bean as skipped by its condition?
		// 我们是否需要标注这个bean是跳过的
		if (this.conditionEvaluator.shouldSkip(metadata, ConfigurationPhase.REGISTER_BEAN)) {
			configClass.skippedBeanMethods.add(methodName);
			return;
		}
		if (configClass.skippedBeanMethods.contains(methodName)) {
			return;
		}

		// Consider name and any aliases
		AnnotationAttributes bean = AnnotationConfigUtils.attributesFor(metadata, Bean.class);
		List<String> names = new ArrayList<String>(Arrays.asList(bean.getStringArray("name")));
		String beanName = (names.size() > 0 ? names.remove(0) : methodName);

		// Register aliases even when overridden
		for (String alias : names) {
			this.registry.registerAlias(beanName, alias);
		}

		// Has this effectively been overridden before (e.g. via XML)?
		if (isOverriddenByExistingDefinition(beanMethod, beanName)) {
			return;
		}

		ConfigurationClassBeanDefinition beanDef = new ConfigurationClassBeanDefinition(configClass, metadata);
		beanDef.setResource(configClass.getResource());
		beanDef.setSource(this.sourceExtractor.extractSource(metadata, configClass.getResource()));

		if (metadata.isStatic()) {
			// static @Bean method
			beanDef.setBeanClassName(configClass.getMetadata().getClassName());
			beanDef.setFactoryMethodName(methodName);
		}
		else {
			// instance @Bean method
			beanDef.setFactoryBeanName(configClass.getBeanName());
			beanDef.setUniqueFactoryMethodName(methodName);
		}
		beanDef.setAutowireMode(RootBeanDefinition.AUTOWIRE_CONSTRUCTOR);
		beanDef.setAttribute(RequiredAnnotationBeanPostProcessor.SKIP_REQUIRED_CHECK_ATTRIBUTE, Boolean.TRUE);

		AnnotationConfigUtils.processCommonDefinitionAnnotations(beanDef, metadata);

		Autowire autowire = bean.getEnum("autowire");
		if (autowire.isAutowire()) {
			beanDef.setAutowireMode(autowire.value());
		}

		String initMethodName = bean.getString("initMethod");
		if (StringUtils.hasText(initMethodName)) {
			beanDef.setInitMethodName(initMethodName);
		}

		String destroyMethodName = bean.getString("destroyMethod");
		if (StringUtils.hasText(destroyMethodName)) {
			beanDef.setDestroyMethodName(destroyMethodName);
		}

		// Consider scoping
		ScopedProxyMode proxyMode = ScopedProxyMode.NO;
		AnnotationAttributes scope = AnnotationConfigUtils.attributesFor(metadata, Scope.class);
		if (scope != null) {
			beanDef.setScope(scope.getString("value"));
			proxyMode = scope.getEnum("proxyMode");
			if (proxyMode == ScopedProxyMode.DEFAULT) {
				proxyMode = ScopedProxyMode.NO;
			}
		}

		// Replace the original bean definition with the target one, if necessary
		BeanDefinition beanDefToRegister = beanDef;
		if (proxyMode != ScopedProxyMode.NO) {
			BeanDefinitionHolder proxyDef = ScopedProxyCreator.createScopedProxy(
					new BeanDefinitionHolder(beanDef, beanName), this.registry, proxyMode == ScopedProxyMode.TARGET_CLASS);
			beanDefToRegister = new ConfigurationClassBeanDefinition(
					(RootBeanDefinition) proxyDef.getBeanDefinition(), configClass, metadata);
		}

		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Registering bean definition for @Bean method %s.%s()",
					configClass.getMetadata().getClassName(), beanName));
		}

		this.registry.registerBeanDefinition(beanName, beanDefToRegister);
	}

	protected boolean isOverriddenByExistingDefinition(BeanMethod beanMethod, String beanName) {
		if (!this.registry.containsBeanDefinition(beanName)) {
			return false;
		}
		BeanDefinition existingBeanDef = this.registry.getBeanDefinition(beanName);

		// Is the existing bean definition one that was created from a configuration class?
		// -> allow the current bean method to override, since both are at second-pass level.
		// However, if the bean method is an overloaded case on the same configuration class,
		// preserve the existing bean definition.
		if (existingBeanDef instanceof ConfigurationClassBeanDefinition) {
			ConfigurationClassBeanDefinition ccbd = (ConfigurationClassBeanDefinition) existingBeanDef;
			return (ccbd.getMetadata().getClassName().equals(beanMethod.getConfigurationClass().getMetadata().getClassName()));
		}

		// Has the existing bean definition bean marked as a framework-generated bean?
		// -> allow the current bean method to override it, since it is application-level
		if (existingBeanDef.getRole() > BeanDefinition.ROLE_APPLICATION) {
			return false;
		}

		// At this point, it's a top-level override (probably XML), just having been parsed
		// before configuration class processing kicks in...
		if (logger.isInfoEnabled()) {
			logger.info(String.format("Skipping bean definition for %s: a definition for bean '%s' " +
					"already exists. This top-level bean definition is considered as an override.",
					beanMethod, beanName));
		}
		return true;
	}

	private void loadBeanDefinitionsFromImportedResources(
			Map<String, Class<? extends BeanDefinitionReader>> importedResources/**通过ImportResource进来的资源*/) {

		Map<Class<?>, BeanDefinitionReader> readerInstanceCache = new HashMap<Class<?>, BeanDefinitionReader>();

		for (Map.Entry<String, Class<? extends BeanDefinitionReader>> entry : importedResources.entrySet()) {
			//拿到名字
			String resource = entry.getKey();
			//拿到阅读器
			Class<? extends BeanDefinitionReader> readerClass = entry.getValue();

			// Default reader selection necessary?
			if (readerClass.equals(BeanDefinitionReader.class)) {
				if (StringUtils.endsWithIgnoreCase(resource, ".groovy")) {
					// When clearly asking for Groovy, that's what they'll get...
					readerClass = GroovyBeanDefinitionReader.class;
				}
				else {
					// Primarily ".xml" files but for any other extension as well
					readerClass = XmlBeanDefinitionReader.class;
				}
			}

			BeanDefinitionReader reader = readerInstanceCache.get(readerClass);
			if (reader == null) {
				try {
					// Instantiate the specified BeanDefinitionReader
					reader = readerClass.getConstructor(BeanDefinitionRegistry.class).newInstance(this.registry);
					// Delegate the current ResourceLoader to it if possible
					if (reader instanceof AbstractBeanDefinitionReader) {
						AbstractBeanDefinitionReader abdr = ((AbstractBeanDefinitionReader) reader);
						abdr.setResourceLoader(this.resourceLoader);
						abdr.setEnvironment(this.environment);
					}
					readerInstanceCache.put(readerClass, reader);
				}
				catch (Exception ex) {
					throw new IllegalStateException(
							"Could not instantiate BeanDefinitionReader class [" + readerClass.getName() + "]");
				}
			}

			// TODO SPR-6310: qualify relative path locations as done in AbstractContextLoader.modifyLocations
			reader.loadBeanDefinitions(resource);
		}
	}

	private void loadBeanDefinitionsFromRegistrars(Map<ImportBeanDefinitionRegistrar, AnnotationMetadata> registrars) {
		for (Map.Entry<ImportBeanDefinitionRegistrar, AnnotationMetadata> entry : registrars.entrySet()) {
			entry.getKey().registerBeanDefinitions(entry.getValue(), this.registry);
		}
	}


	/**
	 * {@link RootBeanDefinition} marker subclass used to signify that a bean definition
	 * was created from a configuration class as opposed to any other configuration source.
	 * Used in bean overriding cases where it's necessary to determine whether the bean
	 * definition was created externally.
	 */
	@SuppressWarnings("serial")
	private static class ConfigurationClassBeanDefinition extends RootBeanDefinition implements AnnotatedBeanDefinition {

		private final AnnotationMetadata annotationMetadata;

		private final MethodMetadata factoryMethodMetadata;

		public ConfigurationClassBeanDefinition(ConfigurationClass configClass, MethodMetadata beanMethodMetadata) {
			this.annotationMetadata = configClass.getMetadata();
			this.factoryMethodMetadata = beanMethodMetadata;
			setLenientConstructorResolution(false);
		}

		public ConfigurationClassBeanDefinition(
				RootBeanDefinition original, ConfigurationClass configClass, MethodMetadata beanMethodMetadata) {
			super(original);
			this.annotationMetadata = configClass.getMetadata();
			this.factoryMethodMetadata = beanMethodMetadata;
		}

		private ConfigurationClassBeanDefinition(ConfigurationClassBeanDefinition original) {
			super(original);
			this.annotationMetadata = original.annotationMetadata;
			this.factoryMethodMetadata = original.factoryMethodMetadata;
		}

		@Override
		public AnnotationMetadata getMetadata() {
			return this.annotationMetadata;
		}

		@Override
		public MethodMetadata getFactoryMethodMetadata() {
			return this.factoryMethodMetadata;
		}

		@Override
		public boolean isFactoryMethod(Method candidate) {
			return (super.isFactoryMethod(candidate) && BeanAnnotationHelper.isBeanAnnotated(candidate));
		}

		@Override
		public ConfigurationClassBeanDefinition cloneBeanDefinition() {
			return new ConfigurationClassBeanDefinition(this);
		}
	}


	/**
	 * Configuration classes must be annotated with {@link Configuration @Configuration} or
	 * declare at least one {@link Bean @Bean} method.
	 */
	private static class InvalidConfigurationImportProblem extends Problem {

		public InvalidConfigurationImportProblem(String className, Resource resource, AnnotationMetadata metadata) {
			super(String.format("%s was @Import'ed but is not annotated with @Configuration " +
					"nor does it declare any @Bean methods; it does not implement ImportSelector " +
					"or extend ImportBeanDefinitionRegistrar. Update the class to meet one of these requirements " +
					"or do not attempt to @Import it.", className), new Location(resource, metadata));
		}
	}


	/**
	 * Evaluate {@code @Conditional} annotations, tracking results and taking into
	 * account 'imported by'.
	 */
	private class TrackedConditionEvaluator {

		private final Map<ConfigurationClass, Boolean> skipped = new HashMap<ConfigurationClass, Boolean>();

		public boolean shouldSkip(ConfigurationClass configClass) {
			Boolean skip = this.skipped.get(configClass);
			if (skip == null) {
				if (configClass.isImported()) {
					boolean allSkipped = true;
					for (ConfigurationClass importedBy : configClass.getImportedBy()) {
						if (!shouldSkip(importedBy)) {
							allSkipped = false;
							break;
						}
					}
					if (allSkipped) {
						// The config classes that imported this one were all skipped, therefore we are skipped...
						skip = true;
					}
				}
				if (skip == null) {
					skip = conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN);
				}
				this.skipped.put(configClass, skip);
			}
			return skip;
		}
	}

}
