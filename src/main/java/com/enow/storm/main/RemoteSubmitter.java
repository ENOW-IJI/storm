package com.enow.storm.main;

/**
 * Created by writtic on 2016. 8. 30..
 */

import com.enow.persistence.redis.RedisDB;
import com.enow.storm.ActionTopology.*;
import com.enow.storm.TriggerTopology.*;
import org.apache.storm.Config;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.*;
import org.apache.storm.spout.SchemeAsMultiScheme;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;

public class RemoteSubmitter {
    private static final String[] TOPICS = new String[]{"event", "proceed", "order", "trigger", "status"};
    private static final String[] SETTINGS = new String[]{"127.0.0.1", "27017", "127.0.0.1", "6379", "127.0.0.1:9092", "127.0.0.1:2181"};
    // private static final String zkhost = "127.0.0.1:2181";
    public static void main(String[] args) throws Exception {
        // RedisDB.getInstance(args[4], Integer.parseInt(args[5])).deleteAllNodes();
        // RedisDB.getInstance(args[4], Integer.parseInt(args[5])).deleteAllStatus();

        RedisDB.getInstance(SETTINGS[2], Integer.parseInt(SETTINGS[3])).deleteAllNodes();
        RedisDB.getInstance(SETTINGS[2], Integer.parseInt(SETTINGS[3])).deleteAllStatus();
        new RemoteSubmitter().runMain(args);
    }

    protected void runMain(String[] args) throws Exception {

        // submitTopologyRemoteCluster(args[0], getTriggerTopology(args[7]), getConfig(args));
        // submitTopologyRemoteCluster(args[1], getActionTopology(args[7]), getConfig(args));

        submitTopologyRemoteCluster(args[0], getTriggerTopology(SETTINGS[5]), getConfig(SETTINGS));
        submitTopologyRemoteCluster(args[1], getActionTopology(SETTINGS[5]), getConfig(SETTINGS));
    }

    protected void submitTopologyRemoteCluster(String arg, StormTopology topology, Config config) throws Exception {
        StormSubmitter.submitTopology(arg, config, topology);
    }

    protected void stopWaitingForInput() {
        try {
            System.out.println("PRESS ENTER TO STOP");
            new BufferedReader(new InputStreamReader(System.in)).readLine();
            System.exit(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected Config getConfig(String[] SETTINGS) {
        Config config = new Config();
        // config.put("mongodb.ip", args[2]);
        // config.put("mongodb.port", Integer.parseInt(args[3]));
        // config.put("redis.ip", args[4]);
        // config.put("redis.port", Integer.parseInt(args[5]));
        // config.put("kafka.properties", args[6]);

        config.put("mongodb.ip", SETTINGS[0]);
        config.put("mongodb.port", Integer.parseInt(SETTINGS[1]));
        config.put("redis.ip", SETTINGS[2]);
        config.put("redis.port", Integer.parseInt(SETTINGS[3]));
        config.put("kafka.properties", SETTINGS[4]);
        config.setDebug(true);
        config.setNumWorkers(4);
        return config;
    }

    protected StormTopology getTriggerTopology(String zkhost) {
        BrokerHosts hosts = new ZkHosts(zkhost);
        TopologyBuilder builder = new TopologyBuilder();
        // event spouts setting
        SpoutConfig eventConfig = new SpoutConfig(hosts, TOPICS[0], "/" + TOPICS[0], UUID.randomUUID().toString());
        eventConfig.scheme = new SchemeAsMultiScheme(new StringScheme());
        eventConfig.startOffsetTime = kafka.api.OffsetRequest.LatestTime();
        eventConfig.ignoreZkOffsets = true;
        // proceed spouts setting
        SpoutConfig proceedConfig = new SpoutConfig(hosts, TOPICS[1], "/" + TOPICS[1], UUID.randomUUID().toString());
        proceedConfig.scheme = new SchemeAsMultiScheme(new StringScheme());
        proceedConfig.startOffsetTime = kafka.api.OffsetRequest.LatestTime();
        proceedConfig.ignoreZkOffsets = true;
        // order spouts setting
        SpoutConfig orderConfig = new SpoutConfig(hosts, TOPICS[2], "/" + TOPICS[2], UUID.randomUUID().toString());
        orderConfig.scheme = new SchemeAsMultiScheme(new StringScheme());
        orderConfig.startOffsetTime = kafka.api.OffsetRequest.LatestTime();
        orderConfig.ignoreZkOffsets = true;

        // Set spouts
        builder.setSpout("event-spout", new KafkaSpout(eventConfig));
        builder.setSpout("proceed-spout", new KafkaSpout(proceedConfig));
        builder.setSpout("order-spout", new KafkaSpout(orderConfig));
        // Set bolts
        builder.setBolt("indexing-bolt", new IndexingBolt()).allGrouping("event-spout")
                .allGrouping("proceed-spout")
                .allGrouping("order-spout");
        builder.setBolt("staging-bolt", new StagingBolt()).fieldsGrouping("indexing-bolt", new Fields("roadMapId"));
        builder.setBolt("calling-trigger-bolt", new CallingTriggerBolt()).fieldsGrouping("staging-bolt", new Fields("roadMapId"));
        return builder.createTopology();
    }
    protected StormTopology getActionTopology(String zkhost) {
        BrokerHosts hosts = new ZkHosts(zkhost);
        TopologyBuilder builder = new TopologyBuilder();
        // trigger spouts setting
        SpoutConfig triggerConfig = new SpoutConfig(hosts, TOPICS[3], "/" + TOPICS[3], UUID.randomUUID().toString());
        triggerConfig.scheme = new SchemeAsMultiScheme(new StringScheme());
        triggerConfig.startOffsetTime = kafka.api.OffsetRequest.LatestTime();
        triggerConfig.ignoreZkOffsets = true;
        // status spouts setting
        SpoutConfig statusConfig = new SpoutConfig(hosts, TOPICS[4], "/" + TOPICS[4], UUID.randomUUID().toString());
        statusConfig.scheme = new SchemeAsMultiScheme(new StringScheme());
        statusConfig.startOffsetTime = kafka.api.OffsetRequest.LatestTime();
        statusConfig.ignoreZkOffsets = true;
        // Set spouts
        builder.setSpout("trigger-spout", new KafkaSpout(triggerConfig));
        builder.setSpout("status-spout", new KafkaSpout(statusConfig));
        /* Set bolts */
        builder.setBolt("scheduling-bolt", new SchedulingBolt())
                .allGrouping("trigger-spout");
        builder.setBolt("status-bolt", new StatusBolt(), 4)
                .allGrouping("status-spout");
        builder.setBolt("execute-code-bolt", new ExecutingBolt()).fieldsGrouping("scheduling-bolt",new Fields("roadMapId"));
        builder.setBolt("provisioning-bolt", new ProvisioningBolt()).fieldsGrouping("execute-code-bolt",new Fields("roadMapId"));
        builder.setBolt("calling-feed-bolt", new CallingFeedBolt()).fieldsGrouping("provisioning-bolt",new Fields("roadMapId"));
        return builder.createTopology();
    }
}
