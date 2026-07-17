import {
  JobResult,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/test_overview';
import {MockTestScenario} from '../models';

/** A mock test scenario representing a failed test. */
export const SCENARIO_TEST_FAILED: MockTestScenario = {
  id: '29226bb4-6e61-455a-aad2-b68a06ffb841',
  scenarioName: 'Failed Test (with Stack Trace)',
  log: '....\n[10:12:39] ANDROID_INSTRUMENTATION_INSTALL_APK_ERROR: Failed to install on internal storage\n....',
  cloudLogLink: '#',
  overview: {
    id: '29226bb4-6e61-455a-aad2-b68a06ffb841',
    name: 'com.google.android.gm.GmailInstrumentationTest#testSync',
    status: TestStatus.TEST_STATUS_DONE,
    result: TestResult.TEST_RESULT_FAIL,
    job: {
      id: 'c3578d2a-776d-49e3-b065-c66624b9d665',
      name: 'com.google.android.gm.GmailInstrumentationTest',
      // status: JobStatus.JOB_STATUS_DONE,
      result: JobResult.JOB_RESULT_FAIL,
      spongeLink: 'http://sponge/mock-job-link',
    },
    devices: {
      device: [
        {
          id: '43021FDAQ000UM',
          type: 'AndroidRealDevice',
        },
      ],
    },
    host: {
      name: 'mt31-dm01-a-x04.moma.google.com',
      ip: '100.107.201.12',
    },
    executionDetails: {
      createTime: '2025-07-09T10:12:10Z',
      startTime: '2025-07-09T10:12:10Z',
      endTime: '2025-07-09T10:12:40Z',
      lastUpdateTime: '2025-07-09T10:12:40Z',
      user: 'dafeni',
      actualUser: 'dafeni@google.com',
    },
    properties: {
      'abi': 'arm64-v8a',
    },
    troubleshooting: {
      resultCause: {
        error: [
          {
            message:
              'com.google.apps.framework.data.DataErrorException: generic::INTERNAL: MobileHarnessException: Failed to get test detail from Moss [MH|INFRA_ISSUE|FE_JOB_SERVICE_RPC_ERROR|40002]',
            trace: `com.google.apps.framework.data.DataErrorException: <eye3 title='n3GXKd: INTERNAL'/> generic::INTERNAL: MobileHarnessException: Failed to get test detail from Moss [MH|INFRA_ISSUE|FE_JOB_SERVICE_RPC_ERROR|40002] at com.google.devtools.mobileharness.fe.v5.job.service.GetTestDetailProducerModule.produceFeResponse(GetTestDetailProducerModule.java:64)
Suppressed: com.google.common.labs.concurrent.LabsFutures$LabeledExecutionException: GraphFuture{key=@com.google.devtools.mobileharness.fe.v5.job.service.Annotation$GetTestDetail com.google.devtools.mobileharness.fe.job.proto.GetTestDetail$GetTestDetailResponse} failed: MobileHarnessException: Failed to get test detail from Moss [MH|INFRA_ISSUE|FE_JOB_SERVICE_RPC_ERROR|40002]
Caused by: java.util.concurrent.ExecutionException: GraphFuture{key=@com.google.apps.framework.producers.PrivateVisibility(module=com.google.devtools.mobileharness.fe.v5.job.service.GetTestDetailProducerModule.class) com.google.devtools.mobileharness.service.moss.proto.MossServiceProto$GetTestDetailResponse} failed: java.lang.NullPointerException: Attempted to inject null into the 2nd parameter of com.google.devtools.mobileharness.fe.v5.job.service.GetTestDetailProducerModule.produceMossResponse but it is not @Nullable
  at com.google.common.util.concurrent.AbstractFuture$Failure.newExecutionException(AbstractFuture.java:170)
  at com.google.common.util.concurrent.AbstractFuture.getDoneValue(AbstractFuture.java:300)
  at com.google.common.util.concurrent.AbstractFutureState.blockingGet(AbstractFutureState.java:236)
  at com.google.common.util.concurrent.Platform.get(Platform.java:54)
  at com.google.common.util.concurrent.AbstractFuture.get(AbstractFuture.java:265)
  at com.google.common.labs.concurrent.LabsFutures.getWithLabel(LabsFutures.java:845)
  at com.google.apps.framework.producers.GraphFuture.get(GraphFuture.java:115)
  at com.google.common.util.concurrent.Uninterruptibles.getUninterruptibly(Uninterruptibles.java:340)
  at com.google.apps.framework.producers.PresentImpl.get(PresentImpl.java:32)
  at com.google.devtools.mobileharness.fe.v5.job.service.GetTestDetailProducerModule.produceFeResponse(GetTestDetailProducerModule.java:62)
  at com.google.devtools.mobileharness.fe.v5.job.service.GetTestDetailProducerModule_produceFeResponse$$invoker.doProduce(Unknown Source)
  at com.google.apps.framework.producers.MethodProducer.produce(MethodProducer.java:198)
  at com.google.apps.framework.producers.NodeScheduler$NodeFuture.runNode(NodeScheduler.java:566)
  at com.google.apps.framework.producers.NodeScheduler$NodeFuture.run(NodeScheduler.java:534)
  at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(Unknown Source)
  at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(Unknown Source)
  at com.google.apps.framework.server.AbstractThreadPoolModule$InitializingThreadFactory.lambda$newThread$0(AbstractThreadPoolModule.java:466)
  at java.base/java.lang.Thread.run(Unknown Source)
Caused by: java.lang.NullPointerException: Attempted to inject null into the 2nd parameter of com.google.devtools.mobileharness.fe.v5.job.service.GetTestDetailProducerModule.produceMossResponse but it is not @Nullable
  at com.google.apps.framework.producers.ProducerParameterMetadata$NpeTranslator.get(ProducerParameterMetadata.java:637)
Caused by: com.google.inject.ProvisionException: Unable to provision, see the following errors:
1) [Guice/NullInjectedIntoNonNullable]: null returned by binding at DerivedBindingsModule.provideUserEmail() but the 2nd parameter userEmail of GetTestDetailProducerModule.produceMossResponse(GetTestDetailProducerModule.java:99) is not @Nullable
  at DerivedBindingsModule.provideUserEmail(DerivedBindingsModule.kt:164)
  at DerivedBindingsModule.provideUserEmail(DerivedBindingsModule.kt:164)
  \\_ installed by: BoqAuthModule -> AuthModule -> DerivedBindingsModule
  at GetTestDetailProducerModule.produceMossResponse(GetTestDetailProducerModule.java:99)
  \\_ for 2nd parameter userEmail
Learn more: https://NullInjectedIntoNonNullable
1 error`,
          },
        ],
      },
    },
    timingBreakdown: {
      createTime: '2025-07-09T10:12:10Z',
      startTime: '2025-07-09T10:12:10Z',
      endTime: '2025-07-09T10:12:40Z',
      stages: [
        {
          name: 'Pre-run Test',
          startTime: '2025-07-09T10:12:10Z',
          endTime: '2025-07-09T10:12:15Z',
        },
        {
          name: 'Run Test:AndroidInstrumentation',
          startTime: '2025-07-09T10:12:15Z',
          endTime: '2025-07-09T10:12:40Z',
        },
      ],
    },
  },
};
