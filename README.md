# Pile
A framework for reactive values.

In the context of this framework, [reactive values](https://en.wikipedia.org/wiki/Reactive_programming)
are wrappers around plain values that offer some extra functionality,
foremost the ability to depend on other reactive values so that they can recompute themselves when the dependencies change.

## Unleash the Power of Responsiveness

Reactive programming is the key to building applications that respond dynamically to changing data and user interactions. It's the secret behind real-time dashboards, interactive user interfaces, and systems that adapt on the fly. This paradigm has found applications in various domains, including:

1. User Interfaces: Craft responsive and interactive user interfaces that update instantly as user inputs and data streams change.

2. Data Processing: Handle streams of data, like sensor readings or financial market updates, by reacting to new information as it arrives.

3. Computational Workflows: Create complex calculations that automatically update when input data or parameters change, reducing manual intervention.

4. System Monitoring: Build monitoring and alerting systems that instantly detect anomalies and respond to critical events.

This is where *Pile* comes in. *Pile* is a Java 8 framework designed to simplify and enhance reactive programming. It enables you to create dynamic, interdependent data structures that adapt in real-time. With features like dynamic dependencies, optionally delayed, suspendable or lazy recomputation, and redirection of write attempts, *Pile* empowers developers to craft highly responsive applications with ease. The extensible interface hierarchy allows for providing interoperable custom implementations, but considering the extensive set of utility and convenience methods and the the general purpose implementations that are highly customizable using lambdas, this need may not even arise.

Explore the capabilities of *Pile*, extensively documented using Javadoc comments, and unlock the full potential of reactive programming in your projects. Whether you're designing cutting-edge user interfaces or complex data-driven workflows, *Pile* provides the tools you need to build applications that thrive in the world of dynamic data and user interactions.

Here's why you should consider using *Pile* in your projects:

1. Basic Reactivity: *Pile* allows reactive values to depend on each other, automatically recalculating when dependencies change. Whether you need real-time updates, data validation, or complex calculations, *Pile* has you covered.

1. Dynamic Dependencies: *Pile* lets your reactive values adapt intelligently by recording and reacting to dynamic dependencies. This ensures efficient and precise updates, enhancing the responsiveness of your applications.

4. Sealing: Secure your data or react flexibly to write access with *Pile*'s sealing feature. You can prohibit or redirect write attempts, ensuring integrity and enabling more complicated data relations than mere unidirectional dependency.

2. Suppress Automatic Behavior: You can flexibly suspend certain automatic behaviors at times when they are unwanted or would be wasteful, such as automatic revalidation, or the memorizing of previously set values of settings.

2. Observability: Easily observe changes in your data with either synchronous or rate-limited listeners, for interactions with the world outside of *Pile*'s dependency graph. *Pile* makes it straightforward to track and respond to value changes, ensuring your applications remain up-to-date. 

6. Flexible Utility Methods: Access a rich set of utility methods to create, transform, and aggregate reactive values effortlessly. *Pile* simplifies common tasks, saving you time and effort.

5. Partial Dependency Updates: When loading data from external sources, *Pile* allows you to manually set parts of the dependency graph while having the rest automatically recomputed. This flexibility simplifies data integration and reduces bandwidth and storage demands.

3. Customizability: Tailor your reactive system to your exact needs. *Pile* offers high customizability, allowing you to fine-tune reactivity according to your application's unique requirements.

7. Minimal Code Dependencies: *Pile* has minimal external dependencies, making it lightweight and easy to integrate into your projects.

5. Debugging Support: Simplify debugging with detailed logging and tracing features. *Pile* helps you pinpoint issues and optimize your application's behavior.



Whether you're building user interfaces, managing real-time data streams, or solving complex computational problems, *Pile* empowers you to create responsive and efficient applications. Explore the capabilities of *Pile* and elevate your reactive programming experience.

*This cheesy introduction was powered by ChatGPT. Please accept my apologies.*

## Feature overview
Here's an overview of the features offered by the *Pile* framework, with pointers to further documentation and relevant parts of the codebase.



 * **Reading**: Reactive values can be read to obtain the plain value they currently hold. 
 This is mainly achieved with the `get()` method.
 * **Validity**: Some types of reactive values may be invalid, that is, they hold no value at all. 
 The validity status of a reactive value can be reified as a reactive boolean using the `validity` method.
 To avoid unnecessary update messages, there is a distinction between the actual validity and the observed validity:
 If a reactive value becomes invalid temporarily, but no explicit requests for its validity status are made during that time,
 then its observed validity remains `true` as if nothing happened.
 * **Writing**: Some types of reactive values can be explicitly written to using the `set` method, with the effect that the plain value they wrap is exchanged for another.
 Reactive values that do not support writing can do this in several ways:
   * Simply don't implement the `WriteValue` interface
   * Silently ignore the write
   * Throw an exception
   * Redirect the write attempt to do something else (See "Sealing")
 * **Observing**: Changes of the wrapped value or the validity status can be observed by adding a `ValueListener`. 
 * **Depdending**: Reactive values that can depend on other reactive values implement the interface `Depender`. 
 Reactive values that others may depend on implement `Dependency`. 
 If a reactive value is changed or invalidated, all its dependers become invalidated, too.
 * **Recomputing**: Some reactive values (those that implement `Pile`) can recompute the plain value they wrap. 
 This happens roughly speaking each time the value is invalid but all its dependencies are valid, or if revalidation is requested explicitly. 
 Also, auto-validation must not currently be suppressed for that reactive value, as can be done with the `suppressAutoValidation` method.
 Also, a `Recomputer` must have been set for that reactive value.
 Also, it must not currently be in a transaction.
 And some other less important conditions.
 The application programmer defines code that describes how to recompute the value. That code is handed a `Recomputation` object that can be used to interact with the recomputation process, such as:
   * `fulfill` the recomputation with a computed value
   * Fulfill it so it remains invalid
   * Fulfill it with the previously held value, if that is available
   * Forget the previously held value
   * Ask which dependencies have actually changed, leading to this `Recomputation`
   * Manually reconfigure the dependencies during the `Recomputation` or after it has been finished (Not recommended, see *Dynamic dependency recording*)
   * Transfer the `Recomputation` to a different `Thread`
 * **Dynamic dependency recording**: Normally, the dependencies of a `Pile` are specified when it is created and remain that way until manually reconfigured.
 A less runtime-efficient, but in some situations much more comfortable and powerful way to deal with dependencies is the `dynamicDependencies` feature, for example
 if the dependencies you actually need to access are conditional on the concrete value wrapped by one of the other dependencies.
 When used, a `Recomputation` is run in a special *scouting* mode during which accesses to other reactive values are recorded each time the dependencies may have changed.
 In scouting mode, fulfilling the `Recomputation` has no effect other than ending the scouting mode and reconfiguring the dependencies according to what was recorded.
 Almost the only thing the application programmer needs to care about is to avoid doing useless nontrivial computations when the `Recomputation` is in scouting mode, 
 and to access the same dependencies during the actual `Recomputation`. Another caveat is that creating reactive values during a recomputation tends to access them, 
 causing the recomputation to start over endlessly because a previously unrecodered `Dependency` is detected. You can avoid that by suspending the dependency recording behavior
 for the lifetime of a given code block.
 * **Transactions**: Let's say we have four reactive values `A`, `R`, `S` and `X`, which depend on each other in a diamond pattern:
 `A` depends on `R` and `S`, which both depend on `X`. Now, if `X` is changed, `R` and `S` will recompute themselves. But we want `A` to recompute itself only once, after both
 `R` and `S` have attained their (possibly) new values, otherwise `A` would maybe recompute itself twice, and, what's worse, do so once with inconsistent inputs. This is why 
 a reactive value that is undergoing change, whether because it is being `set` explicitly, because it is waiting for its ongoing `Recomputation` to be `fulfill`ed, or because
 some of its dependencies are invalid due to being in a transaction themselves, enters a transaction that propagates to all its Dependers for as long as the reactive value is invalid with hope of becoming valid again it is recorded what soon.
 Being in a transaction may invalidate the reactive value, but the plain value it previously held will be remembered; it can be restored and, if a `Recomputation` should be triggered
 at the end of the transaciton, the `Recomputer` can ask for the old value.
 Transactions can also be started manually, for example when setting several values such as indvidiual coordinates of a point.
 * **Value equivalence**: When a the value wrapped by a reactive value changes, be it by means of setting it manually or because it got recomputed, `ValueEvent`s need to be emitted
 and `Depender`s need to be informed that one of their dependencies changed and they should recompute themselves. But what counts as "changed"? 
 You can decide by defining a custom equivalence relation that will be used in the place of `Objects::equals` to compare the old value, if available, to the new one.
 * **Setting with invalid dependencies**: It is possible to manually set a reactive value while some of its dependencies are invalid. This naturally happens
 if a data model is populated with saved values read from permanent storage or transmitted over the network, but only some of the values have been stored/sent to save bandwidth, because they
 can be recomputed from their own dependencies anyway. When the `Recomputation` of such an invalid `Dependency` finishes, its valid `Depender`s should not be invalidated, because they already have
 a perfectly fine value, and what's worse, they may have been written to manually before they were put to storage, so recomputing would lose these manual changes. However, if an invalid `Dependency`
 is set manually instead of via `Recomputation`, *all* its transitive `Depender`s will be invalidated just as they would if the `Dependency` in questions and all the intermediate depender/dependencies
 had been valid.
 * **Destructibility**: The references that a reactive value holds to its `Dependers` are, as such, `WeakReferences` that do not prevent garbage collection. But it would not be wise
 to just drop all strong references to a `Depender` that is no longer needed, because until the garbage collector detects this, it would still be informed about changes to its dependencies
 and recompute itself accordingly, even though no one cares. Hence the `destroy` method, which safely renders a reactive value unusable and disconnected from other objects. It also recursively
 destroys all of its `Depender`s for which it has been declared an *essential* `Dependency`.
 * **Brackets**: A `ValueBracket` represents some kind of effect that should endure for as long as a plain value is held by a certain reactive value: When the reactive value assumes that value, the bracket
 is *opened*, and when it stops wraping that value, it is *closed*. Because the `open` and `close` methods of a `ValueBracket` run while the corresponding reactive value locks an internal mutex, care must be
 taken to not do certain things in them, such as explicitly `destroy`ing a reactive value. If you need to do questionable things that furthermore don't need to happen synchronously with
 the status change, they can be deferred to a `SequentialQueue` which will run them in correct order in a separate thread.
 * **Associations**: Implementors of the `HasAssociations` interface offer a simple, typed key-value store that can be used to associate various things with an object. In the *Pile* framework, this is mainly used
   * to store the upper and lower bounds of a reactive value
   * to memoize functions that take a reactive value as argument
   * to keep a strong reference to another object that otherwise might be garbage collected
 * **Rate limited observation of multiple values**: Suppose you want to react to changes on several `ListenValue`s, but you don't want your handler code to be run each time, only every 50 milliseconds or so. Then you can use a `rateLimited` `ValueListener` that will accumulate the sources of the `ValueEvent`s that happened between runs of the handler code, so you can handle them all at once but still react only to those values that actually have changed.
 * **Lazy validation**: By default, `Pile`s recompute themselves eagerly as soon as possible. The purpose of the "lazy validation" feature is to save unnecessary recomputations be deferring them until the value is actually requested. This feature is not yet mature. The main drawback of the current implementation is that a reactive value should validate itself eagerly if it has at least one eagerly validating but invalid transitive `Depender`. In the future, I hope to give "lazy validation" an overhaul to make using it actually fun.
 * **Transformation**: Suppose you want to apply a certain transformation to a value, and you know that some of its `Dependers` would recompute themselves in a way that the result essentially
 gets transformed with the same transformation (that is, "covariantly" or "homomorphically"), but it would be cheaper to just transform them, too. Or maybe they have been modified
 manually and you want to keep these modifications through the transformation. *Pile* supports this, albeit in a very rudimentary form. It is not advised to try two concurrent transformations
 on overlapping parts of the dependency graph, and you can't change the transformation being propageted.
 * **Corrections**: Corrections can be installed on a `CorrigibleValue` to normalize it or veto its change. For example, you my have a reactive value that is meant to hold a unit vector, but
 you would like to be able to write any nonzero vector to it and have it be normalized automatically, and an attempt to write the zero vector should be refused.
 * **Sealing**: Implementors of `Sealable` can have their instances optionally be `seal`ed: Attempts to write to them can be ignored, log a warning, throw an exception, or redirect the request.
 Redirection is especially useful in situations where the reactive value normally recomputes itself from its dependencies in a partially bijective way. For example, if you use the
 standard utility method `not` to get the inverse of a writable boolean reactive value, then writing to the reactive value that was returned by `not` actually writes the inverse to the original reactive boolean. The sealing may be bypassed by parts of the program that have access to a privileged `WriteValue` that must have been obtained before the main value was sealed.
 * **Default implementaions**: The *Pile* framework comes with a few general purpose implementations of the aforementioned concepts:
   * `Constant`: For reactive values that never change. `Constant` implements `WriteValue`, but writes are silently ignored. (This may change in the future)
   * `Independent`: For reactive values that themselves have no dependencies and no concept of invalidity. `Independent`s are `Sealable`.
   * `PileImpl`: Has all of the aspects except `Sealable`.
   * `SealPile`: Subclass of `PileImpl` that is additionally `Sealable`.
 * **Aspects**: The various possible aspects of a reactive value are factored inteo several interfaces found in the `pile.aspect` package, such as `ReadValue`, `Depender` or `ListenValue`. This allows you to make your own tailor-made implementaions of the aforementioned concepts, or try your hand at an alternative general purpose implementation, while maintaining interoperability. Combinations of aspects such as `ReadListenDependency` are
 found in the `pile.aspect.combinations` package. Some of them offer additional methods that make sense only for certain combinations.
 * **Builders**: Although you could, you need not configure most aspects of a `Pile` manually. The library comes with a couple of handy builder classes for `Pile`s and `Independent`s that
 allow you to specify behavior using fluent interface syntax and lambdas or method handles. Prominently, they handle all the complications of setting up dynamic dependency recording or running the recomputations in a separate thread, possibly after some delay.
 * **Specializations**: Wouldn't is be nice if all `ReadDependency<Boolean>` could have a `not` method? Alas, Java does not offer extension methods. So we have specializations of (almost) the whole
 class hierarchy of reactive values and their aspects for the most important types: `Boolean`, `Integer`, `Double` and `String`. They mainly exist for the mentioned purpose of
 providing additional methods. But there is also the feature that, for example, you can have a reactive double precision value that recomputes itself as the sum (or product, ...) of all its dependencies which implement `ReadValueDouble`. This is slightly dangerous though, because it ignores dependencies that are `ReadValueInt` or just `ReadValue<Double>` even though intuitively
 you'd want them to be included. The static utility methods (of the `Piles` class and elsewhere) are designed to take the more general types as parameters and return the appropriate specialization.
 * **Utilities**: Numerous useful ways are provided to derive a value from one or several others, such as:
   * `validBuffer`: An `Independent` that always has the most recent valid value of another reactive value
   * `rateLimited`: A reactive value that follows another one, but with a limit on how often it changes per second.
   * `choose`: A reactive value that conditionally follows one of several branches, depending on the value of a reactive boolean or the sign of a reactive integer
   * `readOnlyWrapper`: For when you want to give a writable reactive value to untrusted code that shouldn't be able to write to it
   * `field`: A reactive value that follows another reactive value that is extracted from the plain value held by a third reactive value
   * `deref`: A `field` where the extracted reactive value *is* the plain value held by that other reactive value.
   * Comparisons for equality and ordering that yield reactive booleans
   * Aggragations, such as sum, product, conjunction, disjunction, minimum or maximium of several reactive values
   * Diverse mathematical operators
   * And more
 
   These utility methods are either static methods in the `Piles` class or one of the specializations of `Pile`, such as `PileBool`, or they are default methods in the appropriate
   (specialization of a) combination of aspects. There are also utility methods for creating builders and for configuring them to do certain things.
 * **Remembering the last value**: You may intend some `Independent`s to hold a value that is a setting the user can adjust, and for convenience, when the program is run the next time, the setting should be remembered. But when setting the value without user interaction  (e.g. from a file), it should not be remembered. This kind of behaviour is governed by the interfaces `RemembersLastValue`, `LastValueRememberer` and `LastValueRememberSuppressible`. Utilities are provided that allow you to remember the last value in a `Preferences` node.
 * **Relations** Some ways in which reactive values fit together are not easily expressed in terms of dependencies between reactive values. The `pile.relations` package provides some classes that try to maintain
 certain relations between values, such as
   * The values must be made equal to each other (but possibly only when a certain reactive boolean is `true`)
   * At least/At most/exactly one of a group of reactive booleans should be `true`
   * There should be a material implication between two booleans, that is, if the antecedent is `true`, the conclusion must not be `false`.
 * **Debugging**: Finding out why one of your `Pile`s recomputes itself when it shouldn't or vice versa can be complicated. To make this and other debugging tasks easier, there are some mechanisms, most of which are disabled by default and can be switched on using the `DebugEnabled` class, allowing you to inspect what's going on with your debgger of choice:
   * Each `Pile` and `Independent` can have a "name" and also an "owner" (or "parent") reference to something it was derived from, or to the larger data structure it belongs to.
   * If you neglected to give a name to a problematic `Pile` and you still would like to find out what you are dealing with, a stack trace from the creation of each reactive value can be recorded.
   * The `DebugCallback` interface can be used to trace what's going on.
   * The reasons why and where transactions were started and which of them are still not closed can be recorded.
   * The various decisions the lead to the recomputation (or not) of a `PileImpl` can be documented, to be inspected later in the debbuger. 
   Since this makes the program very slow, it is meant to be switched on on a per-value basis.
   * The threads that are busy recomputing values can be renamed according to the name of the `Pile` they recompute so you can more easily find them in the debugger or you get more meaningful log messages.
   * Warnings can be logged or exceptions thrown when problematic usage of the framework is detected, such as `destroy`ing something while a `ValueBracket` is closing, not `fulfill`ing a `Recomputation`, dropping the last strong reference to a `Suppressor` without releasing it first, or creating a reactive value during a `Recomputation` that uses 
   dynamic `Dependency` recording.
   
   The `DebugEnabled` class is in its own source folder so you can easily repalce it with your customized version while leaving the rest of *Pile*'s sources unchanged.
 The flags for which debugging features are enabled are `static final boolean`s to make use of Java's conditional compliation so that the runtime overhead is greatly reduced
 when the debugging features are not in use. Unfortunately, that means that you need to recompile the library each time you change a debugging flag. 
 Since the best way to do that will be dependent on your build environment, I shall refrain from giving advice.
   
The Pile framework has no library dependencies outside the standard library. Within the standard library, it mainly depends on `java.lang(|.ref)` and `java.util(|.function|.concurrent|.logging|.prefs)`. If comes with a small suite of utility classes. There are some dependencies that can be injected; these are concerned with

  * which `ReferenceQueue` should take care of weak and soft `References` created by *Pile*. See class `AbstractReferenceManager`.  
  * which `ExecutorService`s should be used to run jobs in different threads
  * how to `wait`, `notify`, `sleep`, and `interrupt`. The reasons which I can think of why one might want to do this differently from the native semantics of these methods of `Object` and `Thread` are mainly
    * Debugging a thread that is waiting or sleeping indefinitely is harder. Better if it wakes up each second or so, but spelling out the logic for that each time is cumbersome
    and switching it off for a production build is not as straightforward as it should be. 
    * Your program might utilize Java's thread interruption mechanism for something else besides "crash the entire thread". For example, `interrupt` might just mean "wake up, check messages,
    and return to sleep unless you got a special interrupt message". Making the `WaitService` to be used an injectable dependency allows *Pile* to be adapted to such needs.

## About
I developed *Pile* for my employer [Promadent UG](https://www.promadent.de), who kindly gave me permission to publish it as open source. *Pile* is used in our dental software *Biss*, both to manage the workflow in the reactive model and to define the GUI. 

It is called "*Pile*" because a (nuclear) pile is a kind of primitive (nuclear) reactor.

### Limitations
Many computations in the workflow of *Biss* are quite long-running, and the GUI needs to react only at human time scale. Hence, *Pile* is not very optimized for efficiency, as I don't really need it and changing a working system has the avoidable potential to break things. Instead, it focuses on flexibility, debuggability, safety and ease of use (although, given it's Java, it's still quite clunky). In general, *Pile* is the way I need it to be, and functionallity that I don't need may be missing or completely untested.

Some parts of the API, such as certain method names, are unsystematic or otherwise bad. I may change these in the future.

Like basically everything made with Java, *Pile* wishes it was written in Kotlin. At the very least, *Pile* would be much more comfortable to use from Kotlin code. Maybe I'll provide Kotlin bindings in the future.
