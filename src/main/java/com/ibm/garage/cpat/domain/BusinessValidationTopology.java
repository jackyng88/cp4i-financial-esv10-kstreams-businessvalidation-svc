package com.ibm.garage.cpat.domain;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.kafka.client.serialization.JsonbSerde;


@ApplicationScoped
public class BusinessValidationTopology {

    @ConfigProperty(name = "START_TOPIC_NAME")
    private String INCOMING_TOPIC;

    @ConfigProperty(name = "TARGET_TOPIC_NAME")
    private String OUTGOING_TOPIC;


    @Produces
    public Topology buildTopology() {

        StreamsBuilder builder = new StreamsBuilder();

        JsonbSerde<FinancialMessage> financialMessageSerde = new JsonbSerde<>(FinancialMessage.class);

        // A stream processor (node) within the topology (graph of nodes). Here, initially
        // the stream is provided with an "incoming topic" to consume from. This incoming stream
        // has it's messages deserialized with financialMessageSerde and then filtered by calling
        // checkCompliance. If this returns true we call a mapValues with that message to 
        // change the necessary flag to false to indicate the check is complete. Finally we then 
        // send it back to the topic and use the same serde to serialize it into JSON.
        builder.stream(
            INCOMING_TOPIC,
            Consumed.with(Serdes.String(), financialMessageSerde)
        )
        .filter(
            (key, message) -> checkBusinessValidation(message)
        )
        .mapValues (
            checkedMessage -> performBusinessValidationCheck(checkedMessage)
        )
        .to (
            INCOMING_TOPIC,
            Produced.with(Serdes.String(), financialMessageSerde)
        );  
        
        return builder.build();
    }

    public boolean checkBusinessValidation (FinancialMessage rawMessage) {
        // Returns a boolean based on whether compliance_services is false, 
        // technical_validation is false, schema_validation is false, and business_validaton is true.
        return (!rawMessage.compliance_services && !rawMessage.technical_validation
                && !rawMessage.schema_validation && rawMessage.business_validation);
    }

    public FinancialMessage performBusinessValidationCheck(FinancialMessage checkedMessage) {
        // Perform the "check" and then return the transformed object.
        checkedMessage.business_validation = false;

        // If the next check isn't ready, trigger it to happen next.
        if (!checkedMessage.trade_enrichment) {
            checkedMessage.trade_enrichment = !checkedMessage.trade_enrichment;
        }

        return checkedMessage;
    }
}