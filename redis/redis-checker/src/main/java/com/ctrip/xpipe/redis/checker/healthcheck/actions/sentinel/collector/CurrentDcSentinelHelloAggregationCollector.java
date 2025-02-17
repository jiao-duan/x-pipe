package com.ctrip.xpipe.redis.checker.healthcheck.actions.sentinel.collector;

import com.ctrip.xpipe.api.foundation.FoundationService;
import com.ctrip.xpipe.api.monitor.Task;
import com.ctrip.xpipe.api.monitor.TransactionMonitor;
import com.ctrip.xpipe.redis.checker.healthcheck.BiDirectionSupport;
import com.ctrip.xpipe.redis.checker.healthcheck.LocalDcSupport;
import com.ctrip.xpipe.redis.checker.healthcheck.RedisInstanceInfo;
import com.ctrip.xpipe.redis.checker.healthcheck.SingleDcSupport;
import com.ctrip.xpipe.redis.checker.healthcheck.actions.sentinel.SentinelActionContext;
import com.ctrip.xpipe.redis.core.meta.MetaCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class CurrentDcSentinelHelloAggregationCollector extends AbstractAggregationCollector<CurrentDcSentinelHelloCollector> implements BiDirectionSupport, SingleDcSupport, LocalDcSupport {
    protected static Logger logger = LoggerFactory.getLogger(CurrentDcSentinelHelloAggregationCollector.class);

    private MetaCache metaCache;

    private final String dcId = FoundationService.DEFAULT.getDataCenter();

    public CurrentDcSentinelHelloAggregationCollector(MetaCache metaCache, CurrentDcSentinelHelloCollector sentinelHelloCollector,
                                                      String clusterId, String shardId) {
        super(sentinelHelloCollector, clusterId, shardId);
        this.metaCache = metaCache;
    }

    @Override
    public void onAction(SentinelActionContext context) {
        TransactionMonitor transaction = TransactionMonitor.DEFAULT;
        RedisInstanceInfo info = context.instance().getCheckInfo();
        transaction.logTransactionSwallowException("sentinel.check.notify", info.getClusterId(), new Task() {
            @Override
            public void go() throws Exception {
                RedisInstanceInfo info = context.instance().getCheckInfo();
                if (!info.getClusterId().equalsIgnoreCase(clusterId) || !info.getShardId().equalsIgnoreCase(shardId))
                    return;

                if (collectHello(context) >= getRedisCntInCurrentDc() - 1) {
                    if (checkFinishedInstance.size() == checkFailInstance.size()) {
                        logger.info("[{}-{}][onAction] sentinel hello all fail, skip sentinel adjust", clusterId, shardId);
                        resetCheckResult();
                        return;
                    }
                    logger.debug("[{}-{}][onAction] sentinel hello collect finish: {}", clusterId, shardId, checkResult.toString());
                    handleAllHellos(context.instance());
                }
            }

            @Override
            public Map<String, Object> getData() {
                Map<String, Object> transactionData = new HashMap<>();
                transactionData.put("checkRedisInstances", info);
                return transactionData;
            }
        });
    }

    private int getRedisCntInCurrentDc() {
        return metaCache.getRedisOfDcClusterShard(dcId, clusterId, shardId).size();
    }

}
