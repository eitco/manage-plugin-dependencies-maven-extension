import java.nio.charset.StandardCharsets
import java.nio.file.Files
import groovy.xml.XmlParser

File effectivePom = new File(new File("$basedir"), 'target/effective-pom.xml')

assert effectivePom.isFile()

String fileContent = new String(Files.readAllBytes(effectivePom.toPath()), StandardCharsets.UTF_8)

XmlParser xmlParser = new XmlParser()

def project = xmlParser.parseText(fileContent)

def liquibasePluginDependency = project.':build'.':plugins'.':plugin'.find { plugin ->

    plugin.':artifactId'.text() == 'liquibase-maven-plugin'
}.':dependencies'.':dependency'

def jooqMavenPluginDependency = project.':build'.':plugins'.':plugin'.find { plugin ->

    plugin.':artifactId'.text() == 'jooq-codegen-maven'
}.':dependencies'.':dependency'

assert liquibasePluginDependency.':artifactId'.text() == 'postgresql'
assert liquibasePluginDependency.':version'.text() == '42.6.2'
assert jooqMavenPluginDependency.':artifactId'.text() == 'postgresql'
assert jooqMavenPluginDependency.':version'.text() == '42.6.2'


