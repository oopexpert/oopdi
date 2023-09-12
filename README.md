# OOPDI: Lightweight Dependency Injection Framework

1. [Introduction](#introduction)
   - Overview
   - Features

2. [Getting Started](#getting-started)
   - Prerequisites
   - Basic Usage
   - Configuration

3. [Annotations](#annotations)
   - @Injectable
   - @InjectInstance
   - @InjectSet
   - @PostConstruct

4. [Scoping](#scoping)
   - Global Scope
   - Thread Scope
   - Local Scope
   - Request Scope
   - Custom Scopes

5. [Core Concepts](#core-concepts)
   - No Code Generation
   - Object Network and Association
   - "CannotInject" Exception
   - Handling Abstract Classes

6. [Object Parameter Paradigm](#object-parameter-paradigm)
   - Runnable
   - Supplier
   - Consumer
   - Function
   - Enforcing Object Parameter Paradigm

7. [Request Scope](#request-scope)
   - Request Scope Overview
   - Generalized Request Scope
   - Dynamic Request Scope for Instances

8. [Initialization with @PostConstruct](#initialization-with-postconstruct)
   - Purpose and Benefits
   - Single @PostConstruct Method
   - MultiplePostConstructMethodsException

9. [Usage Guidelines](#usage-guidelines)
   - Enforcing Encapsulation
   - Controlled Scope
   - Object Lifecycle Management

10. [Advanced Topics](#advanced-topics)
    - Extending and Customizing
    - Circular Dependencies
    - Handling Errors and Exceptions
    - Testing Strategies

11. [Contributing](#contributing)
    - Code of Conduct
    - How to Contribute
    - Reporting Issues

12. [License](#license)

13. [Acknowledgments](#acknowledgments)

14. [Contact](#contact)

# Introduction

Welcome to OOPDI, a lightweight and versatile Dependency Injection (DI) framework designed to simplify object management and promote best practices in software development. OOPDI empowers developers to efficiently manage dependencies, enforce encapsulation, and streamline the initialization process of objects within their applications.

## Overview

Dependency Injection is a fundamental concept in modern software development, promoting loose coupling, testability, and maintainability of code. OOPDI takes this concept further by introducing innovative features and principles to enhance your dependency management experience.

## Features

OOPDI offers a rich set of features and concepts:

- **Annotations**: Use intuitive annotations like `@Injectable`, `@InjectInstance`, `@InjectSet`, and `@PostConstruct` to define and configure dependencies.

- **Scoping**: Manage the lifecycle and scope of objects with fine-grained control, including Global, Thread, Local, Request, and custom scopes.

- **Core Concepts**: Adhere to principles such as no code generation, object network association, and proper handling of exceptions.

- **Object Parameter Paradigm**: Embrace functional programming by enforcing interactions through `Runnable`, `Supplier`, `Consumer`, and `Function` interfaces.

- **Request Scope**: Manage dependencies within isolated contexts, not limited to HTTP requests, but also applicable to various application scenarios.

- **Initialization with @PostConstruct**: Simplify and standardize object initialization with the `@PostConstruct` method annotation.

- **Usage Guidelines**: Discover best practices for utilizing OOPDI to enforce encapsulation and controlled scope in your projects.

- **Advanced Topics**: Explore advanced concepts, customization options, and strategies for testing and error handling.


