package org.spring.boot.mybatis.rw.starter;

import java.util.Set;

import javax.annotation.PostConstruct;

import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.spring.boot.mybatis.rw.starter.datasource.impl.RoundRobinRWRoutingDataSourceProxy;
import org.spring.boot.mybatis.rw.starter.pulgin.RWPlugin;
import org.spring.boot.mybatis.rw.starter.transaction.RWManagedTransactionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 *
 * @author chenlei
 */
@Configuration()
@ConditionalOnClass({ SqlSessionFactory.class, SqlSessionFactoryBean.class })
@EnableConfigurationProperties(MybatisProperties.class)
@ConditionalOnBean(name={"writeDataSource","readDataSources"}) 
public class MybatisAutoConfiguration {

	@Autowired
	private MybatisProperties properties;

	@Autowired(required = false)
	private Set<Interceptor> interceptors;
	@Autowired
	private ResourceLoader resourceLoader = new DefaultResourceLoader();

	@Autowired(required = false)
	private DatabaseIdProvider databaseIdProvider;

	@PostConstruct
	public void checkConfigFileExists() {
		if (this.properties.isCheckConfigLocation() && StringUtils.hasText(this.properties.getConfigLocation())) {
			Resource resource = this.resourceLoader.getResource(this.properties.getConfigLocation());
			Assert.state(resource.exists(), "Cannot find config location: " + resource
					+ " (please add config file or check your Mybatis configuration)");
		}
	}

	@Bean
	public SqlSessionFactory sqlSessionFactory() throws Exception {
		    
		SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
		Interceptor rwplugin = new RWPlugin();
		if (StringUtils.hasText(this.properties.getConfigLocation())) {
			factory.setConfigLocation(this.resourceLoader.getResource(this.properties.getConfigLocation()));
		}
		factory.setConfiguration(properties.getConfiguration());
		if (CollectionUtils.isEmpty(this.interceptors)) {
			Interceptor[] plugins = { rwplugin };
			factory.setPlugins(plugins);
		} else {
			this.interceptors.add(rwplugin);
			Interceptor[] plugins = new Interceptor[0];
			factory.setPlugins(this.interceptors.toArray(plugins));
		}
		if (this.databaseIdProvider != null) {
			factory.setDatabaseIdProvider(this.databaseIdProvider);
		}
		if (StringUtils.hasLength(this.properties.getTypeAliasesPackage())) {
			factory.setTypeAliasesPackage(this.properties.getTypeAliasesPackage());
		}
		if (StringUtils.hasLength(this.properties.getTypeHandlersPackage())) {
			factory.setTypeHandlersPackage(this.properties.getTypeHandlersPackage());
		}
		if (!ObjectUtils.isEmpty(this.properties.resolveMapperLocations())) {
			factory.setMapperLocations(this.properties.resolveMapperLocations());
		}
		
		factory.setTransactionFactory(new RWManagedTransactionFactory());
		factory.setDataSource(this.roundRobinDataSouceProxy());
		return factory.getObject();
	}

	@Bean
	@Primary
	public RoundRobinRWRoutingDataSourceProxy roundRobinDataSouceProxy() {
		RoundRobinRWRoutingDataSourceProxy proxy = new RoundRobinRWRoutingDataSourceProxy();
		return proxy;
	}

	@Bean
	public DataSourceTransactionManager transactionManager() {
		return new DataSourceTransactionManager(roundRobinDataSouceProxy());
	}
	


	/*
	 * @Bean
	 * 
	 * @ConditionalOnMissingBean public SqlSessionTemplate
	 * sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) { ExecutorType
	 * executorType = this.properties.getExecutorType(); if (executorType !=
	 * null) { return new SqlSessionTemplate(sqlSessionFactory, executorType); }
	 * else { return new SqlSessionTemplate(sqlSessionFactory); } }
	 */

}
