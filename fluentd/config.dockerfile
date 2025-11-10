FROM fluent/fluentd:v1.17-debian

USER root

# Kafka 플러그인 설치
RUN gem install fluent-plugin-kafka --no-document

USER fluent
