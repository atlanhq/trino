/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.hive;

import io.trino.operator.RetryPolicy;
import io.trino.plugin.exchange.filesystem.FileSystemExchangePlugin;
import io.trino.plugin.exchange.filesystem.containers.MinioStorage;
import io.trino.plugin.hive.containers.HiveMinioDataLake;
import io.trino.plugin.hive.s3.S3HiveQueryRunner;
import io.trino.testing.QueryRunner;
import io.trino.tpch.TpchTable;
import org.testng.annotations.AfterClass;

import java.util.List;
import java.util.Map;

import static io.trino.plugin.exchange.filesystem.containers.MinioStorage.getExchangeManagerProperties;
import static io.trino.testing.TestingNames.randomNameSuffix;

public class TestHiveQueryFailureRecoveryTest
        extends BaseHiveFailureRecoveryTest
{
    public TestHiveQueryFailureRecoveryTest()
    {
        super(RetryPolicy.QUERY);
    }

    private HiveMinioDataLake hiveMinioDataLake;
    private MinioStorage minioStorage;

    @Override
    protected QueryRunner createQueryRunner(
            List<TpchTable<?>> requiredTpchTables,
            Map<String, String> configProperties,
            Map<String, String> coordinatorProperties)
            throws Exception
    {
        String bucketName = "test-hive-insert-overwrite-" + randomNameSuffix(); // randomizing bucket name to ensure cached TrinoS3FileSystem objects are not reused
        this.hiveMinioDataLake = new HiveMinioDataLake(bucketName);
        hiveMinioDataLake.start();

        this.minioStorage = new MinioStorage("test-exchange-spooling-" + randomNameSuffix());
        minioStorage.start();

        return S3HiveQueryRunner.builder(hiveMinioDataLake)
                .setInitialTables(requiredTpchTables)
                .setExtraProperties(configProperties)
                .setCoordinatorProperties(coordinatorProperties)
                .setAdditionalSetup(runner -> {
                    runner.installPlugin(new FileSystemExchangePlugin());
                    runner.loadExchangeManager("filesystem", getExchangeManagerProperties(minioStorage));
                })
                .build();
    }

    @AfterClass(alwaysRun = true)
    public void destroy()
            throws Exception
    {
        if (hiveMinioDataLake != null) {
            hiveMinioDataLake.close();
            hiveMinioDataLake = null;
        }
        if (minioStorage != null) {
            minioStorage.close();
            minioStorage = null;
        }
    }
}
