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
   - Object Network and Association
   - Limited Access to Instances
   - Object Parameter Paradigm

5. [Method Execution with OOPDI](#method-execution-with-oopdi)
   - Runnable
   - Supplier
   - Consumer
   - Function
   - Enforcing Object Parameter Paradigm

6. [Request Scope](#request-scope)
   - Understanding Request Scope
   - Example Use Cases
   - Benefits of Request Scope
   - Implementation Considerations
   - Conclusion

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

## Scoping

Scoping is a fundamental concept in OOPDI, enabling you to define how long objects live and under what conditions they can be accessed. OOPDI provides several built-in scopes that cater to different use cases:

- **Global Scope**: Objects with global scope persist throughout the application's lifetime, making them available for use at any time.

- **Thread Scope**: Thread-scoped objects are unique to each thread, ensuring that they are isolated and managed separately in multi-threaded environments.

- **Local Scope**: Local-scoped objects are limited to the context they were created in, allowing for controlled and short-lived dependencies.

- **Request Scope**: Request-scoped objects are specific to a particular unit of work or context, which can be broader than traditional HTTP requests, making them suitable for various application scenarios.

Scoping in OOPDI provides developers with a powerful mechanism to manage object lifecycles, control access, and enforce encapsulation. Explore the [Scoping](#scoping) section to learn more about each scope's characteristics and how to leverage them effectively in your projects.

## Core Concepts

OOPDI is founded on several core concepts that shape its design philosophy and guide its usage:

- **Limited Access to Instances**: OOPDI adheres to a principle where direct access to instances is restricted. Instead, developers interact with instances through method references, following the functional concept of `Runnable`, `Supplier`, `Consumer`, and `Function`. This approach enhances encapsulation and maintains control over object lifecycles.

- **Parameter Object Paradigm**: OOPDI enforces the Parameter Object Paradigm, which emphasizes interactions through functional interfaces—`Runnable`, `Supplier`, `Consumer`, and `Function`. This promotes clean, modular code by ensuring that all interactions with instances follow a functional paradigm.

- **Object Network and Association**: Every class managed by OOPDI is part of an object network derived from a single root class provided during the framework's initialization. Additionally, each class is reachable through associations, ensuring that every dependency is well-defined and accessible in a structured manner.

Explore these core concepts in depth within the framework to harness their benefits and build modular, maintainable applications effectively.

## Method Execution with OOPDI

In the OOPDI (Object-Oriented Dependency Injection) framework, the central OOPDI object provides methods to execute various functional interfaces (Runnable, Supplier, Consumer, Function) within the context of an injectable class.

### Runnable
```
public <T1> void execRunnable(Class<T1> clazz, Function<T1, Runnable> f)
```
Executes a Runnable method within the context of an injectable class specified by clazz. The f parameter represents a method reference that returns a Runnable. This method is suitable for actions that do not require input parameters and do not produce a result.

### Supplier
```
public <T1, Y> Y execSupplier(Class<T1> clazz, Function<T1, Supplier<Y>> f)
```
Executes a Supplier method within the context of an injectable class specified by clazz. The f parameter represents a method reference that returns a Supplier providing a result of type Y. This method is suitable for actions that produce a result without taking any input parameters.

### Function
```
public <T1, X, Y> Y execFunction(Class<T1> clazz, Function<T1, Function<X, Y>> f, X x)
```
Executes a Function method within the context of an injectable class specified by clazz. The f parameter represents a method reference that returns a Function transforming input of type X into a result of type Y. The x parameter is the input value for the function. This method is suitable for actions that transform input into a result.

### Consumer
```
public <T1, X> void execConsumer(Class<T1> clazz, Function<T1, Consumer<X>> f, X x)
```
Executes a Consumer method within the context of an injectable class specified by clazz. The f parameter represents a method reference that returns a Consumer accepting input of type X. The x parameter is the input value for the consumer. This method is suitable for actions that accept input without producing a result.

### Object Parameter Paradigm

In the OOPDI (Object-Oriented Dependency Injection) framework, the Object Parameter Paradigm enforces a design principle where method parameters are always passed as objects, rather than primitive types. This paradigm is particularly enforced due to the usage of functional interfaces such as Runnable, Supplier, Consumer, and Function, which are designed to work with objects.

Why Use the Object Parameter Paradigm?

- Flexibility: By passing parameters as objects, methods can accept a wide range of inputs, including complex data structures and custom types.

- Consistency: Enforcing object parameters promotes consistency across method signatures and encourages a uniform approach to data handling.

- Interoperability: Using objects as parameters enhances interoperability between different parts of the framework and third-party libraries, as objects are universally understood and compatible.

- Functional Programming: The Object Parameter Paradigm aligns with the principles of functional programming, where functions operate on data as first-class citizens, promoting modularity and composability.

Benefits of the Object Parameter Paradigm
- Avoid Primitive Obsession: By treating parameters as objects, the framework avoids the common antipattern of "primitive obsession," where methods rely heavily on primitive data types.

- Encapsulation: Object parameters encapsulate related data into cohesive units, enhancing code readability and maintainability.

- Type Safety: Using objects promotes type safety and reduces the risk of runtime errors by enforcing type checking at compile time.

- Support for Dependency Injection: Object parameters facilitate dependency injection by providing a clear mechanism for passing dependencies to methods and constructors.

Adhering to the Object Parameter Paradigm ensures a consistent and robust approach to parameter passing within the OOPDI framework, promoting code quality, extensibility, and modularity.

## Request Scope
In the OOPDI (Object-Oriented Dependency Injection) framework, the Request Scope represents a scoped instance whose lifespan is tied to a single request. Unlike other scopes such as Global or Thread, where instances persist across multiple invocations, a Request Scoped Instance remains consistent throughout a call hierarchy within the context of a single request.

### Understanding Request Scope
Contextual Lifespan: A Request Scoped Instance is created and managed within the scope of a specific request. This ensures that the instance remains relevant and consistent throughout the processing of that request.

- Call Hierarchy Consistency: Regardless of the depth or complexity of the call hierarchy within a request, all components within that hierarchy share the same instance of the Request Scoped Object. This ensures consistent behavior and data integrity across all components involved in handling the request.

- Lifecycle Management: The OOPDI framework ensures that the Request Scoped Instance is instantiated at the beginning of the request processing and remains active until the completion of the request. Once the request is processed, the instance is typically discarded or made available for garbage collection.

- Isolation Between Requests: Each new request initiates a new instance of the Request Scoped Object, ensuring isolation between different requests. This prevents data leakage or interference between concurrent requests and promotes a clean separation of concerns.

### Example Use Cases
- Web Applications: In a web application context, a Request Scoped Instance may represent objects such as the current user session, request-specific configuration settings, or cached data relevant to the current request processing.

- API Endpoints: When handling API requests, a Request Scoped Instance could encapsulate request parameters, authentication tokens, or transactional context specific to the API call being processed.

- Batch Processing: In batch processing scenarios, a Request Scoped Instance may encapsulate context information or processing state relevant to a particular batch job or task execution.

### Benefits of Request Scope
- Consistency: Ensures consistent behavior and data integrity within the scope of a single request, facilitating predictable and reliable request processing.

- Contextual Relevance: Provides a mechanism for managing context-specific data or state within the context of a request, promoting encapsulation and modularity.

- Resource Efficiency: Optimizes resource usage by limiting the lifespan of the instance to the duration of a single request, reducing memory overhead and potential resource contention.

### Implementation Considerations
Lifecycle Management: Implementations of Request Scoped Instances should carefully manage their lifecycle to ensure proper initialization, usage, and cleanup within the context of each request.

Concurrency Considerations: While Request Scoped Instances are typically isolated between requests, implementations should consider thread safety and concurrency issues when processing concurrent requests.

Dependency Injection: The OOPDI framework provides mechanisms for injecting Request Scoped Instances into components that require access to request-specific data or functionality.

### Conclusion
In the OOPDI framework, the Request Scope offers a powerful mechanism for managing context-specific data and ensuring consistency within the scope of a single request. By providing a clear and consistent lifecycle for request-scoped instances, the framework enables developers to build robust and scalable applications with enhanced modularity and encapsulation.


## Code Examples

In this section, we provide practical code examples that demonstrate how to use OOPDI effectively in various scenarios. These examples serve as quick guides to help you get started with the framework in your own projects. Feel free to explore and adapt them to your specific use cases.

### Basic Dependency Injection

- **Injecting a Singleton**: Show how to use the `@Injectable` annotation to inject a singleton instance of a class.
  
- **Thread-Scoped Dependency**: Demonstrate how to create and manage thread-scoped dependencies using OOPDI.
  
- **Local Scope Usage**: Illustrate the use of local-scoped dependencies within a specific context or method.
  
- **Request Scope in Action**: Showcase how to leverage request-scoped dependencies, including use cases beyond traditional HTTP requests.

### Advanced Dependency Management

- **Managing Collections with `@InjectSet`**: Explore how to use `@InjectSet` to manage and inject sets of dependencies, even when no matching classes are found.

- **Fine-Grained Dependency Injection**: Dive into examples of using `@InjectInstance` for precise, type-specific dependency injection.

### Object Initialization

- **Using `@PostConstruct`**: Walk through the process of initializing objects using the `@PostConstruct` annotation. Highlight how it simplifies object setup and configuration.

### Scoping Strategies

- **Global Scope Example**: Provide a scenario where global-scoped dependencies are essential and demonstrate their usage.

- **Thread Scope in Multithreading**: Showcase thread-scoped dependencies in a multi-threaded environment to ensure thread safety.

- **Local Scope Use Case**: Describe a use case where local-scoped dependencies provide controlled, short-lived functionality.

- **Custom Scopes (if applicable)**: If you have plans to implement custom scopes in the future, you can add examples demonstrating their usage here.

### Advanced Features and Best Practices

- **Circular Dependency Resolution**: Demonstrate how OOPDI resolves circular dependencies to maintain object integrity.

- **Testing Strategies**: Present strategies for testing classes and dependencies managed by OOPDI effectively.

- **Error Handling**: Illustrate best practices for handling exceptions and errors when working with the framework.

These code examples aim to provide practical insights into using OOPDI in your projects. We encourage you to explore these examples, adapt them to your specific requirements, and leverage the full potential of OOPDI for efficient dependency management.

