java -classpath ../lib/sleet.jar:../lib/commons-codec-1.6.jar:../lib/commons-io-2.1.jar:../lib/commons-lang3-3.1.jar:../lib/derby-10.8.2.2.jar \
-Djava.util.logging.config.file="../conf/logging.properties" \
sleet.Sleet \
--host-name=mangstadt.dyndns.org \
--smtp-port=2550 \
--smtp-msa-port=2551 \
--pop3-port=2552 \
--database=../db/dirby \
--smtp-inbound-log=../logs/smtp-inbound-transactions.log \
--smtp-msa-log=../logs/smtp-msa-transactions.log \
--smtp-outbound-log=../logs/smtp-outbound-transactions.log \
--pop3-log=../logs/pop3-transactions.log