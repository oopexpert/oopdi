# OOPDI: Lightweight Dependency Injection Framework

1. [Introduction](#introduction)
   - Overview
   - Features

2. [Annotations](#annotations)
   - @Injectable
   - @InjectInstance
   - @InjectSet
   - @PostConstruct

3. [Scoping](#scoping)
   - Global Scope
   - Thread Scope
   - Local Scope
   - Request Scope

4. [Core Concepts](#core-concepts)
   - No Code Generation
   - Object Network and Association
   - Object Parameter Paradigm

5. [Object Parameter Paradigm](#object-parameter-paradigm)
   - Runnable
   - Supplier
   - Consumer
   - Function
   - Enforcing Object Parameter Paradigm

6. [Request Scope](#request-scope)
   - Request Scope Overview
   - Generalized Request Scope
   - Dynamic Request Scope for Instances

7. [Initialization with @PostConstruct](#initialization-with-postconstruct)
   - Purpose and Benefits
   - Single @PostConstruct Method
   - MultiplePostConstructMethodsException

8. [Usage Guidelines](#usage-guidelines)
   - Enforcing Encapsulation
   - Controlled Scope
   - Object Lifecycle Management

9. [Advanced Topics](#advanced-topics)
    - Extending and Customizing
    - Circular Dependencies
    - Handling Errors and Exceptions
    - Testing Strategies

10. [Contributing](#contributing)
    - Code of Conduct
    - How to Contribute
    - Reporting Issues

11. [License](#license)

12. [Acknowledgments](#acknowledgments)

13. [Contact](#contact)

## Introduction

Welcome to OOPDI, a lightweight and versatile Dependency Injection (DI) framework designed to simplify object management and promote best practices in software development. OOPDI empowers developers to efficiently manage dependencies, enforce encapsulation, and streamline the initialization process of objects within their applications.

### Overview

Dependency Injection is a fundamental concept in modern software development, promoting loose coupling, testability, and maintainability of code. OOPDI takes this concept further by introducing innovative features and principles to enhance your dependency management experience.

### Features

OOPDI offers a rich set of features and concepts:

- **Annotations**: Use intuitive annotations like `@Injectable`, `@InjectInstance`, `@InjectSet`, and `@PostConstruct` to define and configure dependencies.

- **Scoping**: Manage the lifecycle and scope of objects with fine-grained control, including Global, Thread, Local, Request, and custom scopes.

- **Core Concepts**: Adhere to principles such as no code generation, object network association, and proper handling of exceptions.

- **Object Parameter Paradigm**: Embrace functional programming by enforcing interactions through `Runnable`, `Supplier`, `Consumer`, and `Function` interfaces.

- **Request Scope**: Manage dependencies within isolated contexts, not limited to HTTP requests, but also applicable to various application scenarios.

- **Initialization with @PostConstruct**: Simplify and standardize object initialization with the `@PostConstruct` method annotation.

- **Usage Guidelines**: Discover best practices for utilizing OOPDI to enforce encapsulation and controlled scope in your projects.

- **Advanced Topics**: Explore advanced concepts, customization options, and strategies for testing and error handling.

## Annotations

### `@Injectable`

The `@Injectable` annotation is at the heart of OOPDI's dependency injection system. It allows you to mark classes as injectable, indicating that they can be managed by the framework and injected into other parts of your application. `@Injectable` provides flexible scoping options, allowing you to define whether a class should have a global, thread-local, local, or request-specific scope. This powerful annotation empowers you to control the lifecycle and accessibility of objects within your application, promoting modularization and efficient dependency management.

### `@InjectInstance`

The `@InjectInstance` annotation is a powerful tool for fine-grained dependency injection within your application. With `@InjectInstance`, you can specify precise injection points for dependencies based on their types, enabling you to tailor object retrieval to specific use cases. This annotation allows you to inject instances of classes or interfaces directly, giving you full control over the injection process. Whether you need to fetch a unique instance or a collection of instances, `@InjectInstance` provides the flexibility to retrieve the right dependencies when and where you need them.

### `@InjectSet`

The `@InjectSet` annotation is a versatile tool that simplifies the management of collections of dependencies. With `@InjectSet`, you can preinitialize sets of objects, even when no classes of the specified type are found. This annotation provides a convenient way to handle scenarios where you need to work with sets of objects, whether they are dynamically discovered or predefined. `@InjectSet` offers a "hint" attribute, allowing you to specify the correct type to inject, addressing Java generics' runtime type erasure.

### `@PostConstruct`

The `@PostConstruct` annotation is a powerful tool for handling object initialization within your application. It allows you to designate a single method in a class as the initialization routine, ensuring that it runs after the class's construction, but before it's made available for use. With `@PostConstruct`, you can streamline the setup of your objects, execute necessary configuration steps, and ensure that dependencies are ready for use. This annotation enforces a single initialization method per class, promoting code clarity and predictable object lifecycles.

### Scoping

Scoping is a fundamental concept in OOPDI, enabling you to define how long objects live and under what conditions they can be accessed. OOPDI provides several built-in scopes that cater to different use cases:

- **Global Scope**: Objects with global scope persist throughout the application's lifetime, making them available for use at any time.

- **Thread Scope**: Thread-scoped objects are unique to each thread, ensuring that they are isolated and managed separately in multi-threaded environments.

- **Local Scope**: Local-scoped objects are limited to the context they were created in, allowing for controlled and short-lived dependencies.

- **Request Scope**: Request-scoped objects are specific to a particular unit of work or context, which can be broader than traditional HTTP requests, making them suitable for various application scenarios.

Scoping in OOPDI provides developers with a powerful mechanism to manage object lifecycles, control access, and enforce encapsulation. Explore the [Scoping](#scoping) section to learn more about each scope's characteristics and how to leverage them effectively in your projects.

### Core Concepts

OOPDI is founded on several core concepts that shape its design philosophy and guide its usage:

- **Parameter Object Paradigm**: OOPDI enforces the Parameter Object Paradigm, which emphasizes interactions through functional interfaces—`Runnable`, `Supplier`, `Consumer`, and `Function`. This promotes clean, modular code by ensuring that all interactions with instances follow a functional paradigm.

- **Limited Access to Instances**: OOPDI adheres to a principle where direct access to instances is restricted. Instead, developers interact with instances through method references, following the functional concept of `Runnable`, `Supplier`, `Consumer`, and `Function`. This approach enhances encapsulation and maintains control over object lifecycles.

- **No Code Generation**: OOPDI takes pride in its code simplicity. It achieves its functionality without resorting to on-the-fly code generation, keeping your codebase clean, predictable, and easy to maintain.

- **Object Network and Association**: Every class managed by OOPDI is part of an object network derived from a single root class provided during the framework's initialization. Additionally, each class is reachable through associations, ensuring that every dependency is well-defined and accessible in a structured manner.

Explore these core concepts in depth within the framework to harness their benefits and build modular, maintainable applications effectively.
