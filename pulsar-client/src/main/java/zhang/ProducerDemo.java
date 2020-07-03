/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package zhang;

import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;

/**
 * ProducerDemo
 */
public class ProducerDemo {
    public static void main(String[] args) throws PulsarClientException {
        PulsarClient client = PulsarClient.builder()
                .serviceUrl("pulsar://localhost:6650")
                .build();

        Producer<byte[]> producer = client.newProducer()
                .topic("my-topic")
                .create();

        for (int i = 0; i < 100; i++) {
            // You can then send messages to the broker and topic you specified:
            MessageId messageId = null;
                    producer.newMessage()
                    .key("my-message-key")
                    .value("my-async-message".getBytes())
                    .property("my-key", "my-value")
                    .property("my-other-key", "my-other-value")
                    .sendAsync();
            System.out.println("send " + i);
//            System.out.printf("send %s%n", messageId.toString());
        }
        producer.close();
        client.close();
    }
}

