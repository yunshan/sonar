/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2008-2012 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.core;

import com.google.common.collect.ImmutableList;
import org.sonar.api.*;
import org.sonar.api.checks.NoSonarFilter;
import org.sonar.api.config.PropertySetDefinitions;
import org.sonar.api.config.TestPropertySetTemplate;
import org.sonar.api.resources.Java;
import org.sonar.plugins.core.batch.ExcludedResourceFilter;
import org.sonar.plugins.core.batch.IndexProjectPostJob;
import org.sonar.plugins.core.batch.MavenInitializer;
import org.sonar.plugins.core.batch.ProjectFileSystemLogger;
import org.sonar.plugins.core.charts.DistributionAreaChart;
import org.sonar.plugins.core.charts.DistributionBarChart;
import org.sonar.plugins.core.charts.XradarChart;
import org.sonar.plugins.core.colorizers.JavaColorizerFormat;
import org.sonar.plugins.core.dashboards.*;
import org.sonar.plugins.core.filters.MyFavouritesFilter;
import org.sonar.plugins.core.filters.ProjectFilter;
import org.sonar.plugins.core.filters.TreeMapFilter;
import org.sonar.plugins.core.security.ApplyProjectRolesDecorator;
import org.sonar.plugins.core.security.DefaultResourcePermissions;
import org.sonar.plugins.core.sensors.*;
import org.sonar.plugins.core.testdetailsviewer.TestsViewerDefinition;
import org.sonar.plugins.core.timemachine.*;
import org.sonar.plugins.core.web.Lcom4Viewer;
import org.sonar.plugins.core.widgets.*;
import org.sonar.plugins.core.widgets.actionPlans.ActionPlansWidget;
import org.sonar.plugins.core.widgets.reviews.*;

import java.util.List;

@Properties({
  @Property(
    key = CoreProperties.SERVER_BASE_URL,
    defaultValue = CoreProperties.SERVER_BASE_URL_DEFAULT_VALUE,
    name = "Server base URL",
    description = "HTTP URL of this Sonar server, such as <i>http://yourhost.yourdomain/sonar</i>. This value is used i.e. to create links in emails.",
    project = false,
    global = true,
    category = CoreProperties.CATEGORY_GENERAL),
  @Property(
    key = "toto",
    name = "Toto",
    global = true,
    type = PropertyType.PROPERTY_SET,
    propertySetName = "myset",
    category = CoreProperties.CATEGORY_GENERAL),
  @Property(
    key = CoreProperties.PROJECT_LANGUAGE_PROPERTY,
    defaultValue = Java.KEY,
    name = "Default language",
    description = "Default language of the source code to analyse.",
    project = false,
    global = true,
    category = CoreProperties.CATEGORY_GENERAL),
  @Property(
    key = CoreProperties.PROJECT_EXCLUSIONS_PROPERTY,
    name = "Exclusions",
    description = "Exclude sources from code analysis. Changes will be applied during next code analysis.",
    project = true,
    global = true,
    multiValues = true,
    category = CoreProperties.CATEGORY_EXCLUSIONS),
  @Property(
    key = CoreProperties.CORE_COVERAGE_PLUGIN_PROPERTY,
    defaultValue = "jacoco",
    name = "Code coverage plugin",
    description = "Key of the code coverage plugin to use.",
    project = true,
    global = true,
    category = CoreProperties.CATEGORY_CODE_COVERAGE),
  @Property(
    key = CoreProperties.CORE_IMPORT_SOURCES_PROPERTY,
    defaultValue = "" + CoreProperties.CORE_IMPORT_SOURCES_DEFAULT_VALUE,
    name = "Import sources",
    description = "Set to false if sources should not be displayed, e.g. for security reasons.",
    project = true,
    module = true,
    global = true,
    category = CoreProperties.CATEGORY_SECURITY,
    type = PropertyType.BOOLEAN),
  @Property(
    key = CoreProperties.CORE_TENDENCY_DEPTH_PROPERTY,
    defaultValue = "" + CoreProperties.CORE_TENDENCY_DEPTH_DEFAULT_VALUE,
    name = "Tendency period",
    description = TendencyDecorator.PROP_DAYS_DESCRIPTION,
    project = false,
    global = true,
    category = CoreProperties.CATEGORY_DIFFERENTIAL_VIEWS,
    type = PropertyType.INTEGER),
  @Property(
    key = CoreProperties.SKIP_TENDENCIES_PROPERTY,
    defaultValue = "" + CoreProperties.SKIP_TENDENCIES_DEFAULT_VALUE,
    name = "Skip tendencies",
    description = "Skip calculation of measure tendencies",
    project = true,
    module = false,
    global = true,
    category = CoreProperties.CATEGORY_DIFFERENTIAL_VIEWS,
    type = PropertyType.BOOLEAN),
  @Property(
    key = CoreProperties.CORE_SKIPPED_MODULES_PROPERTY,
    name = "Exclude modules",
    description = "Maven artifact ids of modules to exclude.",
    project = true,
    global = false,
    multiValues = true,
    category = CoreProperties.CATEGORY_GENERAL),
  @Property(
    key = CoreProperties.CORE_FORCE_AUTHENTICATION_PROPERTY,
    defaultValue = "" + CoreProperties.CORE_FORCE_AUTHENTICATION_DEFAULT_VALUE,
    name = "Force user authentication",
    description = "Forcing user authentication stops un-logged users to access Sonar.",
    project = false,
    global = true,
    category = CoreProperties.CATEGORY_SECURITY,
    type = PropertyType.BOOLEAN),
  @Property(
    key = CoreProperties.CORE_ALLOW_USERS_TO_SIGNUP_PROPERTY,
    defaultValue = "" + CoreProperties.CORE_ALLOW_USERS_TO_SIGNUP_DEAULT_VALUE,
    name = "Allow users to sign up online",
    description = "Users can sign up online.",
    project = false,
    global = true,
    category = CoreProperties.CATEGORY_SECURITY,
    type = PropertyType.BOOLEAN),
  @Property(
    key = CoreProperties.CORE_DEFAULT_GROUP,
    defaultValue = CoreProperties.CORE_DEFAULT_GROUP_DEFAULT_VALUE,
    name = "Default user group",
    description = "Any new users will automatically join this group.",
    project = false,
    global = true,
    category = CoreProperties.CATEGORY_SECURITY),
  @Property(
    key = CoreProperties.CORE_VIOLATION_LOCALE_PROPERTY,
    defaultValue = "en",
    name = "Locale used for violation messages",
    description = "Locale to be used when generating violation messages. It's up to each rule engine to support this global internationalization property",
    project = true,
    global = true,
    category = CoreProperties.CATEGORY_L10N),
  @Property(
    key = "sonar.timemachine.period1",
    name = "Period 1",
    description = "Period used to compare measures and track new violations. Values are : <ul class='bullet'><li>Number of days before " +
      "analysis, for example 5.</li><li>A custom date. Format is yyyy-MM-dd, for example 2010-12-25</li><li>'previous_analysis' to " +
      "compare to previous analysis</li><li>'previous_version' to compare to the previous version in the project history</li></ul>" +
      "<p>When specifying a number of days or a date, the snapshot selected for comparison is " +
      " the first one available inside the corresponding time range. </p>" +
      "<p>Changing this property only takes effect after subsequent project inspections.<p/>",
    project = false,
    global = true,
    defaultValue = CoreProperties.TIMEMACHINE_DEFAULT_PERIOD_1,
    category = CoreProperties.CATEGORY_DIFFERENTIAL_VIEWS),
  @Property(
    key = "sonar.timemachine.period2",
    name = "Period 2",
    description = "See the property 'Period 1'",
    project = false,
    global = true,
    defaultValue = CoreProperties.TIMEMACHINE_DEFAULT_PERIOD_2,
    category = CoreProperties.CATEGORY_DIFFERENTIAL_VIEWS),
  @Property(
    key = "sonar.timemachine.period3",
    name = "Period 3",
    description = "See the property 'Period 1'",
    project = false,
    global = true,
    defaultValue = CoreProperties.TIMEMACHINE_DEFAULT_PERIOD_3,
    category = CoreProperties.CATEGORY_DIFFERENTIAL_VIEWS),
  @Property(
    key = "sonar.timemachine.period4",
    name = "Period 4",
    description = "Period used to compare measures and track new violations. This property is specific to the project. Values are : " +
      "<ul class='bullet'><li>Number of days before analysis, for example 5.</li><li>A custom date. Format is yyyy-MM-dd, " +
      "for example 2010-12-25</li><li>'previous_analysis' to compare to previous analysis</li>" +
      "<li>'previous_version' to compare to the previous version in the project history</li><li>A version, for example 1.2</li></ul>" +
      "<p>When specifying a number of days or a date, the snapshot selected for comparison is the first one available inside the corresponding time range. </p>" +
      "<p>Changing this property only takes effect after subsequent project inspections.<p/>",
    project = true,
    global = false,
    defaultValue = CoreProperties.TIMEMACHINE_DEFAULT_PERIOD_4,
    category = CoreProperties.CATEGORY_DIFFERENTIAL_VIEWS),
  @Property(
    key = "sonar.timemachine.period5",
    name = "Period 5",
    description = "See the property 'Period 4'",
    project = true,
    global = false,
    defaultValue = CoreProperties.TIMEMACHINE_DEFAULT_PERIOD_5,
    category = CoreProperties.CATEGORY_DIFFERENTIAL_VIEWS),

  // SERVER-SIDE TECHNICAL PROPERTIES

  @Property(
    key = "sonar.security.realm",
    name = "Security Realm",
    project = false,
    global = false
  ),
  @Property(
    key = "sonar.security.savePassword",
    name = "Save external password",
    project = false,
    global = false
  ),
  @Property(
    key = "sonar.authenticator.downcase",
    name = "Downcase login",
    description = "Downcase login during user authentication, typically for Active Directory",
    project = false,
    global = false,
    defaultValue = "false",
    type = PropertyType.BOOLEAN),
  @Property(
    key = CoreProperties.CORE_AUTHENTICATOR_CREATE_USERS,
    name = "Create user accounts",
    description = "Create accounts when authenticating users via an external system",
    project = false,
    global = false,
    defaultValue = "true",
    type = PropertyType.BOOLEAN),
  @Property(
    key = CoreProperties.CORE_AUTHENTICATOR_IGNORE_STARTUP_FAILURE,
    name = "Ignore failures during authenticator startup",
    defaultValue = "false",
    project = false,
    global = false,
    type = PropertyType.BOOLEAN)
})
public final class CorePlugin extends SonarPlugin {

  @SuppressWarnings("unchecked")
  public List<Class<? extends Extension>> getExtensions() {
    return ImmutableList.of(
      DefaultResourceTypes.class,
      UserManagedMetrics.class,
      ProjectFileSystemLogger.class,

      // maven
      MavenInitializer.class,

      // languages
      Java.class,

      // pages
      TestsViewerDefinition.class,
      Lcom4Viewer.class,

      // filters
      ProjectFilter.class,
      TreeMapFilter.class,
      MyFavouritesFilter.class,

      // widgets
      AlertsWidget.class,
      CoverageWidget.class,
      ItCoverageWidget.class,
      CommentsDuplicationsWidget.class,
      DescriptionWidget.class,
      ComplexityWidget.class,
      RulesWidget.class,
      SizeWidget.class,
      EventsWidget.class,
      CustomMeasuresWidget.class,
      TimelineWidget.class,
      TimeMachineWidget.class,
      HotspotMetricWidget.class,
      HotspotMostViolatedResourcesWidget.class,
      HotspotMostViolatedRulesWidget.class,
      MyReviewsWidget.class,
      ProjectReviewsWidget.class,
      FalsePositiveReviewsWidget.class,
      ReviewsPerDeveloperWidget.class,
      PlannedReviewsWidget.class,
      UnplannedReviewsWidget.class,
      ActionPlansWidget.class,
      ReviewsMetricsWidget.class,
      TreemapWidget.class,
      FilterWidget.class,

      // dashboards
      DefaultDashboard.class,
      HotspotsDashboard.class,
      ReviewsDashboard.class,
      TimeMachineDashboard.class,
      ProjectsDashboard.class,
      TreemapDashboard.class,
      MyFavouritesDashboard.class,

      // Property sets
      PropertySetDefinitions.class,
      TestPropertySetTemplate.class,

      // chart
      XradarChart.class,
      DistributionBarChart.class,
      DistributionAreaChart.class,

      // colorizers
      JavaColorizerFormat.class,

      // batch
      ProfileSensor.class,
      ProfileEventsSensor.class,
      ProjectLinksSensor.class,
      UnitTestDecorator.class,
      VersionEventsSensor.class,
      CheckAlertThresholds.class,
      GenerateAlertEvents.class,
      ViolationsDecorator.class,
      WeightedViolationsDecorator.class,
      ViolationsDensityDecorator.class,
      LineCoverageDecorator.class,
      CoverageDecorator.class,
      BranchCoverageDecorator.class,
      ItLineCoverageDecorator.class,
      ItCoverageDecorator.class,
      ItBranchCoverageDecorator.class,
      DefaultResourcePermissions.class,
      ApplyProjectRolesDecorator.class,
      ExcludedResourceFilter.class,
      CommentDensityDecorator.class,
      NoSonarFilter.class,
      DirectoriesDecorator.class,
      FilesDecorator.class,
      ReviewNotifications.class,
      ReviewWorkflowDecorator.class,
      ReferenceAnalysis.class,
      ManualMeasureDecorator.class,
      ManualViolationInjector.class,
      ViolationSeverityUpdater.class,
      IndexProjectPostJob.class,
      ReviewsMeasuresDecorator.class,

      // time machine
      TendencyDecorator.class,
      VariationDecorator.class,
      ViolationTrackingDecorator.class,
      ViolationPersisterDecorator.class,
      NewViolationsDecorator.class,
      TimeMachineConfigurationPersister.class,
      NewCoverageFileAnalyzer.class,
      NewItCoverageFileAnalyzer.class,
      NewCoverageAggregator.class);
  }
}
