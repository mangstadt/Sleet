java -classpath ../lib/sleet.jar:../lib/commons-codec-1.6.jar:../lib/commons-io-2.1.jar:../lib/commons-lang3-3.1.jar:../lib/derby-10.8.2.2.jar \
-Djava.util.logging.config.file="../conf/logging.properties" \
sleet.Sleet \
--hostName=mangstadt.dyndns.org \
--smtpPort=2550 \
--pop3Port=2551 \
--database=../db \
--smtp-server-log=../logs/smtp-transactions.log \
--smtp-client-log=../logs/smtp-client-transactions.log \
--pop3-log=../logs/pop3-transactions.log