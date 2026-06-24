# Malling Bio IMS

Backend service for managing screen state, timing plans, and simulated cinema show flow for Malling Bio.

## Purpose

This project provides the backend for the Malling Bio screen supervision demo and future operational workflows.

The service is responsible for:

- screen state transitions
- timing plan handling
- manual and automatic show events
- backend simulation for development and demo use
- API endpoints used by the dashboard frontend

## Key Concepts

The backend models a cinema screen lifecycle such as:

- idle
- intro loop
- starting
- advertisements
- trailers
- feature
- ending
- prepare next

It supports both:

- **automatic mode**, where timing-driven logic advances the show
- **manual mode**, where an operator triggers key events

## Project Status

This project is currently used for:

- development
- demo scenarios
- validation of state transitions
- testing of frontend/backend interaction

Production integration with real cinema/IMS hardware is not yet the goal of this version.

## Related Project

Frontend dashboard:

- ../malling-bio-dashboard  
  If the relative link does not work on GitHub, replace it with the full repository URL later.

## Technology

- Java
- Quarkus
- REST API
- Domain-driven state handling
- Dev simulation support

## Running Locally

### Prerequisites

- Java 21 (or your chosen project version)
- Gradle

### Start in dev mode

```bash
./gradlew quarkusDev
