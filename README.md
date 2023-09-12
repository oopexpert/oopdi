# OOPDI: Lightweight Dependency Injection Framework

1. [Introduction](#introduction)
   - Overview
   - Features
   - Installation

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
