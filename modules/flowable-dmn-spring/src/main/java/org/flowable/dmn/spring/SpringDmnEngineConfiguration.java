/* Licensed under the Apache License, Version 2.0 (the "License");
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

package org.flowable.dmn.spring;

import java.util.ArrayList;
import java.util.Collection;

import javax.sql.DataSource;

import org.flowable.dmn.engine.DmnEngine;
import org.flowable.dmn.engine.DmnEngineConfiguration;
import org.flowable.dmn.engine.DmnEngines;
import org.flowable.dmn.engine.impl.cfg.StandaloneDmnEngineConfiguration;
import org.flowable.dmn.engine.impl.interceptor.CommandInterceptor;
import org.flowable.dmn.spring.autodeployment.AutoDeploymentStrategy;
import org.flowable.dmn.spring.autodeployment.DefaultAutoDeploymentStrategy;
import org.flowable.dmn.spring.autodeployment.ResourceParentFolderAutoDeploymentStrategy;
import org.flowable.dmn.spring.autodeployment.SingleResourceAutoDeploymentStrategy;
import org.flowable.engine.common.api.FlowableException;
import org.flowable.engine.common.impl.interceptor.CommandConfig;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * @author Tijs Rademakers
 * @author David Syer
 * @author Joram Barrez
 */
public class SpringDmnEngineConfiguration extends DmnEngineConfiguration implements ApplicationContextAware {

  protected PlatformTransactionManager transactionManager;
  protected String deploymentName = "SpringAutoDeployment";
  protected Resource[] deploymentResources = new Resource[0];
  protected String deploymentMode = "default";
  protected ApplicationContext applicationContext;
  protected Integer transactionSynchronizationAdapterOrder;
  protected Collection<AutoDeploymentStrategy> deploymentStrategies = new ArrayList<AutoDeploymentStrategy>();

  public SpringDmnEngineConfiguration() {
    this.transactionsExternallyManaged = true;
    deploymentStrategies.add(new DefaultAutoDeploymentStrategy());
    deploymentStrategies.add(new SingleResourceAutoDeploymentStrategy());
    deploymentStrategies.add(new ResourceParentFolderAutoDeploymentStrategy());
  }

  @Override
  public DmnEngine buildDmnEngine() {
    DmnEngine dmnEngine = super.buildDmnEngine();
    DmnEngines.setInitialized(true);
    autoDeployResources(dmnEngine);
    return dmnEngine;
  }

  public void setTransactionSynchronizationAdapterOrder(Integer transactionSynchronizationAdapterOrder) {
    this.transactionSynchronizationAdapterOrder = transactionSynchronizationAdapterOrder;
  }

  @Override
  public void initDefaultCommandConfig() {
    if (defaultCommandConfig == null) {
      defaultCommandConfig = new CommandConfig().setContextReusePossible(true);
    }
  }

  @Override
  public CommandInterceptor createTransactionInterceptor() {
    if (transactionManager == null) {
      throw new FlowableException("transactionManager is required property for SpringDmnEngineConfiguration, use " + StandaloneDmnEngineConfiguration.class.getName() + " otherwise");
    }

    return new SpringTransactionInterceptor(transactionManager);
  }

  @Override
  public void initTransactionContextFactory() {
    if (transactionContextFactory == null && transactionManager != null) {
      transactionContextFactory = new SpringTransactionContextFactory(transactionManager, transactionSynchronizationAdapterOrder);
    }
  }

  protected void autoDeployResources(DmnEngine dmnEngine) {
    if (deploymentResources != null && deploymentResources.length > 0) {
      final AutoDeploymentStrategy strategy = getAutoDeploymentStrategy(deploymentMode);
      strategy.deployResources(deploymentName, deploymentResources, dmnEngine.getDmnRepositoryService());
    }
  }

  @Override
  public DmnEngineConfiguration setDataSource(DataSource dataSource) {
    if (dataSource instanceof TransactionAwareDataSourceProxy) {
      return super.setDataSource(dataSource);
    } else {
      // Wrap datasource in Transaction-aware proxy
      DataSource proxiedDataSource = new TransactionAwareDataSourceProxy(dataSource);
      return super.setDataSource(proxiedDataSource);
    }
  }

  public PlatformTransactionManager getTransactionManager() {
    return transactionManager;
  }

  public void setTransactionManager(PlatformTransactionManager transactionManager) {
    this.transactionManager = transactionManager;
  }

  public String getDeploymentName() {
    return deploymentName;
  }

  public void setDeploymentName(String deploymentName) {
    this.deploymentName = deploymentName;
  }

  public Resource[] getDeploymentResources() {
    return deploymentResources;
  }

  public void setDeploymentResources(Resource[] deploymentResources) {
    this.deploymentResources = deploymentResources;
  }

  public ApplicationContext getApplicationContext() {
    return applicationContext;
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }

  public String getDeploymentMode() {
    return deploymentMode;
  }

  public void setDeploymentMode(String deploymentMode) {
    this.deploymentMode = deploymentMode;
  }

  /**
   * Gets the {@link AutoDeploymentStrategy} for the provided mode. This method may be overridden to implement custom deployment strategies if required, but implementors should take care not to return
   * <code>null</code>.
   * 
   * @param mode
   *          the mode to get the strategy for
   * @return the deployment strategy to use for the mode. Never <code>null</code>
   */
  protected AutoDeploymentStrategy getAutoDeploymentStrategy(final String mode) {
    AutoDeploymentStrategy result = new DefaultAutoDeploymentStrategy();
    for (final AutoDeploymentStrategy strategy : deploymentStrategies) {
      if (strategy.handlesMode(mode)) {
        result = strategy;
        break;
      }
    }
    return result;
  }

}
