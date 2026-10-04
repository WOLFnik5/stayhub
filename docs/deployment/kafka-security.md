# Налаштування клієнтів Kafka

Producer, Telegram consumer і Boot Kafka Admin використовують стандартні
`spring.kafka` properties. Спільні `spring.kafka.properties.*` передаються обом
factories, а `spring.kafka.producer.properties.*` та
`spring.kafka.consumer.properties.*` можуть перевизначити відповідні клієнтські
параметри. Це стосується TLS/SASL, request/delivery timeouts, compression, acks
і consumer poll settings. [Контракт KafkaProperties](https://docs.spring.io/spring-boot/4.0/api/java/org/springframework/boot/kafka/autoconfigure/KafkaProperties.html).

Формат подій застосунку залишається текстовим: key/value serializers та
deserializers примусово використовують String. Група Telegram —
`${spring.kafka.consumer.group-id}-telegram`; налаштовуйте її через
`spring.kafka.consumer.group-id`, а не raw `group.id`. Snappy є default лише
за відсутності явно заданої compression. `spring.kafka.listener.auto-startup`
керує запуском Telegram listener; власні retry/DLT та tracing збережені.

## Адреси локального стеку

Для API в IDE використовуйте `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`.
Контейнер API отримує `KAFKA_CONTAINER_BOOTSTRAP_SERVERS`, за замовчуванням
`kafka:29092`. Таким чином значення для локального процесу не підміняє адресу
контейнерного broker на `localhost`. Для зовнішнього broker явно задайте
контейнерну змінну його адресами.

## Зовнішній SASL_SSL broker

Локальний Compose broker залишається PLAINTEXT. Для вже налаштованого broker
із SASL/PLAIN поверх TLS передайте додатковий Spring configuration file, наприклад:

```yaml
spring:
  kafka:
    security:
      protocol: SASL_SSL
    ssl:
      trust-store-location: file:/run/secrets/kafka-truststore.p12
      trust-store-password: ${KAFKA_TRUSTSTORE_PASSWORD}
      trust-store-type: PKCS12
    properties:
      "[sasl.mechanism]": PLAIN
      "[sasl.jaas.config]": ${KAFKA_SASL_JAAS_CONFIG}
      "[ssl.endpoint.identification.algorithm]": https
    producer:
      acks: all
      properties:
        "[delivery.timeout.ms]": 30000
        "[request.timeout.ms]": 10000
    consumer:
      max-poll-records: 100
```

Змонтуйте файл і truststore лише для читання у контейнер API. Додайте
`SPRING_CONFIG_ADDITIONAL_LOCATION=file:/run/config/kafka-client.yml` до його
середовища через власний Compose override. JAAS та пароль truststore передавайте
через secret manager або приватне середовище процесу; не записуйте реальні
секрети в tracked YAML/README. Для PLAIN значення JAAS має форму
`org.apache.kafka.common.security.plain.PlainLoginModule required username="..." password="...";`.

SASL-механізм, сертифікати, логіни та broker ACL мають відповідати фактичному
broker. Не вимикайте hostname verification для обходу помилки сертифіката.
Producer/consumer factories підтримують файлові truststore/keystore properties;
Spring SSL bundles у цих власних factories окремо не підключені.
Цей приклад налаштовує клієнтів, а не розгортає захищений кластер.

## Перевірки

`KafkaClientConfigurationTest` перевіряє Spring binding, фактичні maps factories,
client overrides, wire format і вимкнення listener startup.
`KafkaSecureConnectionIntegrationTest` створює тимчасовий сертифікат і Docker
Kafka broker із SASL_SSL, перевіряє producer → consumer delivery та відхилення
неправильного пароля і недовіреного сертифіката. Він не використовує реальні
секрети чи дані та зупиняє свій контейнер після тестів.
