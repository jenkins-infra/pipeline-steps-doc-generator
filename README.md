# pipeline-plugin-doc-generator
This projects generates the documentation for pipeline jobs.

## Development

In order to install and run the project on your local machine, a rough outline of the steps is provided below.

### 1. Get repositories

You will need to clone the following repositories

* this repo (`jenkins-infra/pipeline-steps-doc-generator`)
* `jenkins-infra/jenkins.io`
* `jenkinsci/schedule-build-plugin` (this plugin is only used as an easy example; you can run similar commands for other plugins too)

> Make sure that the file structure on your local machine is matching with the one mentioned above

You will need to temporarily patch `jenkins.io` as follows:

```diff
diff --git a/scripts/fetch-external-resources b/scripts/fetch-external-resources
index d3ee8319..cf8e38d2 100755
--- a/scripts/fetch-external-resources
+++ b/scripts/fetch-external-resources
@@ -25,12 +25,6 @@ RESOURCES = [
     nil,
     nil
   ],
-  [
-    'https://ci.jenkins.io/job/Infra/job/pipeline-steps-doc-generator/job/master/lastSuccessfulBuild/artifact/allAscii.zip',
-    'content/_tmp/allAscii.zip',
-    nil,
-    'content/doc/pipeline/steps'
-  ],
   [
     'https://repo.jenkins-ci.org/api/search/versions?g=org.jenkins-ci.main&a=jenkins-core&repos=releases&v=?.*.1',
     'content/_data/_generated/lts_baselines.yml',
```

### 2. Create content

You will need to manually create a Makefile inside `jenkinsci/schedule-build-plugin`; then add the following:

> The `copy-plugins` command copies the plugins into the target folder

```
TAG=$(shell date -I -u)
IMAGE=jenkinsci/schedule-build-plugin

copy-plugins:
	if [ \! -f target/test-classes/test-dependencies/index -o \
	     pom.xml -nt target/test-classes/test-dependencies/index ]; then \
	    mvn clean validate hpi:resolve-test-dependencies; fi
	rm -rf plugins
	mkdir plugins
	cp -v target/test-classes/test-dependencies/*.hpi plugins
 ```


Next, run the following commands from this repository (with others in relative positions):

```bash
rm -v ../jenkins.io/content/doc/pipeline/steps/*.adoc
```

> This command runs the makefile created earlier
```
make -C ../../jenkinsci/schedule-build-plugin copy-plugins
```
```
mvn clean install
```

Next, execute the following line to run this project and generate the documentation.
> Note: In case you're working with another plugin, you can replace the path after `-homeDir $(pwd)/../../` with that of your plugin

```
mvn "-Dexec.args=-classpath %classpath org.jenkinsci.pipeline_steps_doc_generator.PipelineStepExtractor -homeDir $(pwd)/../../jenkinsci/schedule-build-plugin -asciiDest $(pwd)/../jenkins.io/content/doc/pipeline/steps -declarativeDest /tmp/declarative" -Dexec.executable=$(which java) org.codehaus.mojo:exec-maven-plugin:3.0.0:exec
```
Finally, build and run the jenkins website
```
make -C ../jenkins.io run
```


Then browse to: http://localhost:4242/doc/pipeline/steps/
