# pipeline-plugin-doc-generator
Creates the documentation for pipeline jobs

## Development

Rough outline of interactive development process:

### Get repositories

You will need

* this repo (`jenkins-infra/pipeline-steps-doc-generator`)
* `jenkins-infra/jenkins.io`
* `jenkinsci/workflow-aggregator-plugin` (as an easy example)

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

### Create content

From this repository, with others in relative positions:

```bash
rm -v ../jenkins.io/content/doc/pipeline/steps/*.adoc
make -C ../../jenkinsci/workflow-aggregator-plugin/demo copy-plugins
mvn "-Dexec.args=-classpath %classpath org.jenkinsci.pipeline_steps_doc_generator.PipelineStepExtractor -homeDir $(pwd)/../../jenkinsci/workflow-aggregator-plugin/demo -asciiDest $(pwd)/../jenkins.io/content/doc/pipeline/steps -declarativeDest /tmp/declarative" -Dexec.executable=$(which java) org.codehaus.mojo:exec-maven-plugin:3.0.0:exec
make -C ../jenkins.io run
```

Then browse: http://localhost:4242/doc/pipeline/steps/
