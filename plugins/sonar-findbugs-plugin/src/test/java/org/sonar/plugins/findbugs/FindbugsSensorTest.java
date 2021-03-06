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
package org.sonar.plugins.findbugs;

import com.google.common.collect.Lists;
import org.apache.commons.configuration.Configuration;
import org.junit.Test;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.InputFile;
import org.sonar.api.resources.InputFileUtils;
import org.sonar.api.resources.JavaFile;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.ProjectFileSystem;
import org.sonar.api.resources.Resource;
import org.sonar.api.rules.Rule;
import org.sonar.api.rules.Violation;
import org.sonar.api.test.IsViolation;

import java.io.File;
import java.util.ArrayList;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FindbugsSensorTest extends FindbugsTests {

  @Test
  public void shouldNotAnalyseIfNoJavaProject() {
    Project project = mock(Project.class);
    when(project.getLanguageKey()).thenReturn("php");

    FindbugsSensor sensor = new FindbugsSensor(null, null, null);
    assertThat(sensor.shouldExecuteOnProject(project)).isFalse();
  }

  @Test
  public void shouldNotAnalyseIfJavaProjectButNoSource() {
    Project project = mock(Project.class);
    ProjectFileSystem fs = mock(ProjectFileSystem.class);
    when(fs.mainFiles("java")).thenReturn(new ArrayList<InputFile>());
    when(project.getFileSystem()).thenReturn(fs);
    when(project.getLanguageKey()).thenReturn("java");

    FindbugsSensor sensor = new FindbugsSensor(null, null, null);
    assertThat(sensor.shouldExecuteOnProject(project)).isFalse();
  }

  @Test
  public void shouldNotAnalyseIfJavaProjectButNoRules() {
    Project project = mock(Project.class);
    ProjectFileSystem fs = mock(ProjectFileSystem.class);
    when(fs.mainFiles("java")).thenReturn(Lists.newArrayList(InputFileUtils.create(null, "")));
    when(project.getFileSystem()).thenReturn(fs);
    when(project.getLanguageKey()).thenReturn("java");

    FindbugsSensor sensor = new FindbugsSensor(RulesProfile.create(), null, null);
    assertThat(sensor.shouldExecuteOnProject(project)).isFalse();
  }

  @Test
  public void shouldAnalyse() {
    Project project = mock(Project.class);
    ProjectFileSystem fs = mock(ProjectFileSystem.class);
    when(fs.mainFiles("java")).thenReturn(Lists.newArrayList(InputFileUtils.create(null, "")));
    when(project.getFileSystem()).thenReturn(fs);
    when(project.getLanguageKey()).thenReturn("java");

    FindbugsSensor sensor = new FindbugsSensor(createRulesProfileWithActiveRules(), null, null);
    assertThat(sensor.shouldExecuteOnProject(project)).isTrue();
  }

  @Test
  public void shouldExecuteFindbugsWhenNoReportProvided() throws Exception {
    Project project = createProject();
    FindbugsExecutor executor = mock(FindbugsExecutor.class);
    SensorContext context = mock(SensorContext.class);
    Configuration conf = mock(Configuration.class);
    // We assume that this report was generated during findbugs execution
    File xmlFile = new File(getClass().getResource("/org/sonar/plugins/findbugs/findbugsReport.xml").toURI());
    when(project.getConfiguration()).thenReturn(conf);
    when(executor.execute()).thenReturn(xmlFile);
    when(context.getResource(any(Resource.class))).thenReturn(new JavaFile("org.sonar.MyClass"));

    FindbugsSensor analyser = new FindbugsSensor(createRulesProfileWithActiveRules(), new FakeRuleFinder(), executor);
    analyser.analyse(project, context);

    verify(executor).execute();
    verify(context, times(2)).saveViolation(any(Violation.class));

    Violation wanted = Violation.create((Rule) null, new JavaFile("org.sonar.commons.ZipUtils")).setMessage(
        "Empty zip file entry created in org.sonar.commons.ZipUtils._zip(String, File, ZipOutputStream)").setLineId(107);
    verify(context).saveViolation(argThat(new IsViolation(wanted)));

    wanted = Violation.create((Rule) null, new JavaFile("org.sonar.commons.resources.MeasuresDao")).setMessage(
        "The class org.sonar.commons.resources.MeasuresDao$1 could be refactored into a named _static_ inner class").setLineId(56);
    verify(context).saveViolation(argThat(new IsViolation(wanted)));
  }

  @Test
  public void shouldReuseReport() throws Exception {
    Project project = createProject();
    FindbugsExecutor executor = mock(FindbugsExecutor.class);
    SensorContext context = mock(SensorContext.class);
    Configuration conf = mock(Configuration.class);
    File xmlFile = new File(getClass().getResource("/org/sonar/plugins/findbugs/findbugsReport.xml").toURI());
    when(conf.getString(CoreProperties.FINDBUGS_REPORT_PATH)).thenReturn(xmlFile.getAbsolutePath());
    when(project.getConfiguration()).thenReturn(conf);
    when(context.getResource(any(Resource.class))).thenReturn(new JavaFile("org.sonar.MyClass"));

    FindbugsSensor analyser = new FindbugsSensor(createRulesProfileWithActiveRules(), new FakeRuleFinder(), executor);
    analyser.analyse(project, context);

    verify(executor, never()).execute();
    verify(context, times(2)).saveViolation(any(Violation.class));

    Violation wanted = Violation.create((Rule) null, new JavaFile("org.sonar.commons.ZipUtils")).setMessage(
        "Empty zip file entry created in org.sonar.commons.ZipUtils._zip(String, File, ZipOutputStream)").setLineId(107);
    verify(context).saveViolation(argThat(new IsViolation(wanted)));

    wanted = Violation.create((Rule) null, new JavaFile("org.sonar.commons.resources.MeasuresDao")).setMessage(
        "The class org.sonar.commons.resources.MeasuresDao$1 could be refactored into a named _static_ inner class").setLineId(56);
    verify(context).saveViolation(argThat(new IsViolation(wanted)));
  }

  @Test
  public void shouldIgnoreNotActiveViolations() throws Exception {
    Project project = createProject();
    FindbugsExecutor executor = mock(FindbugsExecutor.class);
    SensorContext context = mock(SensorContext.class);
    Configuration conf = mock(Configuration.class);
    File xmlFile = new File(getClass().getResource("/org/sonar/plugins/findbugs/findbugsReportWithUnknownRule.xml").toURI());
    when(conf.getString(CoreProperties.FINDBUGS_REPORT_PATH)).thenReturn(xmlFile.getAbsolutePath());
    when(project.getConfiguration()).thenReturn(conf);
    when(context.getResource(any(Resource.class))).thenReturn(new JavaFile("org.sonar.MyClass"));

    FindbugsSensor analyser = new FindbugsSensor(createRulesProfileWithActiveRules(), new FakeRuleFinder(), executor);
    analyser.analyse(project, context);

    verify(context, never()).saveViolation(any(Violation.class));
  }

  private Project createProject() {
    ProjectFileSystem fileSystem = mock(ProjectFileSystem.class);
    when(fileSystem.hasJavaSourceFiles()).thenReturn(Boolean.TRUE);

    Project project = mock(Project.class);
    when(project.getFileSystem()).thenReturn(fileSystem);
    return project;
  }

}
