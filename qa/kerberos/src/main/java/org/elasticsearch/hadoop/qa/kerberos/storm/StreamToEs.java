/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.hadoop.qa.kerberos.storm;

import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;

import org.apache.storm.Config;
import org.apache.storm.StormSubmitter;
import org.apache.storm.shade.com.google.common.collect.ImmutableList;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;
import org.elasticsearch.hadoop.cfg.ConfigurationOptions;
import org.elasticsearch.hadoop.security.LoginUtil;
import org.elasticsearch.storm.EsBolt;
import org.elasticsearch.storm.security.AutoElasticsearch;

public class StreamToEs {
    public static void main(String[] args) throws Exception {
        final String submitPrincipal = args[0];
        final String submitKeytab = args[1];
        LoginContext loginContext = LoginUtil.keytabLogin(submitPrincipal, submitKeytab);
        try {
            Subject.doAs(loginContext.getSubject(), new PrivilegedExceptionAction<Void>() {
                @Override
                public Void run() throws Exception {
                    submitJob(submitPrincipal, submitKeytab);
                    return null;
                }
            });
        } finally {
            loginContext.logout();
        }
    }

    public static void submitJob(String principal, String keytab) throws Exception {
        List doc1 = Collections.singletonList("{\"reason\" : \"business\",\"airport\" : \"SFO\"}");
        List doc2 = Collections.singletonList("{\"participants\" : 5,\"airport\" : \"OTP\"}");

        TopologyBuilder builder = new TopologyBuilder();
        builder.setSpout("Input", new TestSpout(ImmutableList.of(doc1, doc2), new Fields("json"), true));
        builder.setBolt("ES", new EsBolt("storm-test"))
                .shuffleGrouping("Input")
                .addConfiguration(Config.TOPOLOGY_TICK_TUPLE_FREQ_SECS, 2);

        // Nimbus needs to be started with the cred renewer and credentials plugins set in its config file

        Config conf = new Config();
        List<Object> plugins = new ArrayList<Object>();
        plugins.add(AutoElasticsearch.class.getName());
        conf.put(Config.TOPOLOGY_AUTO_CREDENTIALS, plugins);
        conf.put(ConfigurationOptions.ES_PORT, "9500");
        conf.put(ConfigurationOptions.ES_SECURITY_AUTHENTICATION, "kerberos");
        conf.put(ConfigurationOptions.ES_NET_SPNEGO_AUTH_ELASTICSEARCH_PRINCIPAL, "HTTP/build.elastic.co@BUILD.ELASTIC.CO");
        conf.put(ConfigurationOptions.ES_INPUT_JSON, "true");
        StormSubmitter.submitTopology("test-run", conf, builder.createTopology());
    }
}
