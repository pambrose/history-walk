jdk:
  - openjdk11
install:
  - ./gradlew backendMainClasses
  - ./gradlew publishBackendPublicationToMavenLocal
