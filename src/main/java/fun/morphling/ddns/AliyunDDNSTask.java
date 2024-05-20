package fun.morphling.ddns;

import com.aliyun.auth.credentials.Credential;
import com.aliyun.auth.credentials.provider.StaticCredentialProvider;
import com.aliyun.sdk.service.alidns20150109.AsyncClient;
import com.aliyun.sdk.service.alidns20150109.models.*;
import com.google.gson.Gson;
import darabonba.core.client.ClientOverrideConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;


@Component
public class AliyunDDNSTask {

    private static final Logger log = LoggerFactory.getLogger(AliyunDDNSTask.class);

    @Value("${aliyun.accessKeyId}")
    private String accessKeyId;

    @Value("${aliyun.accessSecret}")
    private String accessSecret;

    @Value("${ddns.domain}")
    private String domain;

    @Scheduled(cron = "0 0/10 * * * ?")
    public void ddns() throws Exception {
        ProcessBuilder process = new ProcessBuilder("curl", "6.ipw.cn");
        Process p;
        StringBuilder ipv6 = new StringBuilder();
        try {
            p = process.start();
            InputStreamReader inputStreamReader = new InputStreamReader(p.getInputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                ipv6.append(line);
            }
            p.destroy();
            inputStreamReader.close();
            reader.close();
            log.info("current ipv6 address is: {}", ipv6);
        } catch (IOException e) {
            log.error("IO exception", e);
        }
        // Configure Credentials authentication information, including ak, secret, token
        StaticCredentialProvider provider = StaticCredentialProvider.create(Credential.builder()
                // Please ensure that the environment variables ALIBABA_CLOUD_ACCESS_KEY_ID and ALIBABA_CLOUD_ACCESS_KEY_SECRET are set.
                .accessKeyId(accessKeyId)
                .accessKeySecret(accessSecret)
                //.securityToken(System.getenv("ALIBABA_CLOUD_SECURITY_TOKEN")) // use STS token
                .build());


        // Configure the Client
        AsyncClient client = AsyncClient.builder()
                .region("cn-beijing") // Region ID
                //.httpClient(httpClient) // Use the configured HttpClient, otherwise use the default HttpClient (Apache HttpClient)
                .credentialsProvider(provider)
                //.serviceConfiguration(Configuration.create()) // Service-level configuration
                // Client-level configuration rewrite, can set Endpoint, Http request parameters, etc.
                .overrideConfiguration(
                        ClientOverrideConfiguration.create()
                                // Endpoint 请参考 https://api.aliyun.com/product/Alidns
                                .setEndpointOverride("alidns.cn-beijing.aliyuncs.com")
                                .setConnectTimeout(Duration.ofSeconds(30))
                )
                .build();


        // Parameter settings for API request
        DescribeDomainRecordsRequest describeDomainRecordsRequest = DescribeDomainRecordsRequest.builder()
                .domainName(domain)
                // Request-level configuration rewrite, can set Http request parameters, etc.
                // .requestConfiguration(RequestConfiguration.create().setHttpHeaders(new HttpHeaders()))
                .build();

        // Asynchronously get the return value of the API request
        CompletableFuture<DescribeDomainRecordsResponse> domainRecordResponseFuture = client.describeDomainRecords(describeDomainRecordsRequest);
        // Synchronously get the return value of the API request
        DescribeDomainRecordsResponse domainRecordResponse = domainRecordResponseFuture.get();
        System.out.println(domainRecordResponseFuture.isDone());
        DescribeDomainRecordsResponseBody.Record record = domainRecordResponse.getBody().getDomainRecords().getRecord().get(0);

        log.info("domain record response is: {}", new Gson().toJson(record));

        if (ipv6.toString().equals(record.getValue())) {
            log.info("无需刷新, {}", ipv6);
            client.close();
            return;
        }

        // Parameter settings for API request
        UpdateDomainRecordRequest updateDomainRecordRequest = UpdateDomainRecordRequest.builder()
                .rr("@")
                .recordId(record.getRecordId())
                .type("AAAA")
                .value(ipv6.toString())
                .TTL(600L)
                // Request-level configuration rewrite, can set Http request parameters, etc.
                // .requestConfiguration(RequestConfiguration.create().setHttpHeaders(new HttpHeaders()))
                .build();

        // Asynchronously get the return value of the API request
        CompletableFuture<UpdateDomainRecordResponse> updateDomainRecordResponseFuture = client.updateDomainRecord(updateDomainRecordRequest);
        // Synchronously get the return value of the API request
        UpdateDomainRecordResponse updateDomainRecordResponse = updateDomainRecordResponseFuture.get();
        log.info("update domain record response is: {}", new Gson().toJson(updateDomainRecordResponse));

        client.close();
    }


}
