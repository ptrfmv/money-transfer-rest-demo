# Overview
A simple demo app implementing a REST API for money transfers between accounts. REST entry point is implemented using JAX-RS.
H2 is used as an in-memory storage. Data persists for as long as the JVM runs.

# Testing
JUnit API tests are located in [ApiTest.java](/src/test/java/ru/ptrofimov/demo/rest/ApiTest.java). Test methods make HTTP requests to a Jetty instance created upon test invocation.
The main class is [App.java](/src/main/java/ru/ptrofimov/demo/App.java). It launches a Jetty server instance which can be used for manual API tests.