# Introduction to Simulation - Jerry Banks

“Simulation studies aid in providing understanding about how a system really operates rather than indicating an individual’s predictions about how a system will operate.”

A **discrete-event** model attempts to represent the components of a system and their interactions, including a detailed representation of the actual internal behavior of each component. These models are dynamic and the passage of time plays a crucial role in the simulations.
Most **mathematical**, **statistical** and **input-output** models represent the internals of the system using mathematical or statistical relationships. It is also very common for these models to be static, representing the system at a fixed point in time.

**Event** - occurrence that changes the state of the system. In our context, can be sending, receiving and processing messages, as well as periodically scheduled operations. Events can be internal or external:
- **Internal Event** - an event happening within the system being simulated. In our case, this can be a node creating a block, sending a block message, etc.
- **External Event** - an event that is not created by the part of the system we want to study, but has effects on its behavior. In our case this can be the creation of transactions.

The **System State Variables** the collection of all the information needed to define what is happening within the system, as well as to obtain the necessary information to produce the simulation results. These should be defined during the **modelling process**.
**Continuous** models usually have system variables that change continuously over time, whereas **discrete-event** models have system variables that change only at certain well-defined points in time, where events occur.

An **entity** is an object in the system whose behavior needs to be defined. An entity can be dynamic (is part of, and interacts with, the system) or static (its “services” or “operations” are used by other entities). A static entity can also be referred to as a resource. Entities can have their own state, separate from the system’s state. In our case, nodes will be dynamic entities, and the network will be a static entity as its operations will be used by nodes to communicate with each other, for example.

Steps for a simulation study:
- Define what is the problem
- Define the concrete results that the simulation should provide
- Define the model and its components (system state variables, entities, resources)
- Coding the model
- Verification
- Validation


---

# A Generalized Agent Based Framework for Modeling a Blockchain System - Macal

In the presented formal model, a blockchain system **BC** consists of market agents **X**, miner agents **Y**, transactions **T** in the pending transaction queue **PTQ**, and the ledger **L**.
- A market agent takes part in bilateral transactions. Possesses an identifier **i** and a wallet **w**.
- A miner agent creates and validates blocks. Possesses an identifier **i**, a wallet **w**, the compute power required to solve proof of work puzzle **pwr** and the associated mining cost per unit of power, **cost**.
- Transactions are denoted by **Tn(i,j,v,g,t,o)**, where **n** is the identifier of a unique transaction, **i** is the source, **j** is the recipient, **v** is the value, **g** is the transaction fee, **t** is the timestamp associated with the transaction’s creation, and **o** is either 1 or 0, representing if the transaction is verified or not.
- **PTQ** is a queue of unverified transactions (**T1...Tn** where **T(o)=0**).
The ledger **L** is a vector of verified transactions (**T1...Tn** where **T(o)=1**).
- At a certain point in time, a transaction cannot be in both PTQ and L.

Appending to the ledger:
- A miner selects **x** transactions from the **PTQ** to form a block. It then verifies the respective transactions. When that block is verified by at least **u** other agents, it gets added to the ledger **L**.

Overall the simulator’s steps described in the paper seem simplified, and I think the fact that the simulation engine progresses through fixed time-steps provides less flexibility than discrete-event simulation. However, the way transactions and the pending transaction queue are modeled may be appropriate for our use case.

---

# Inside Discrete-Event Simulation Software: How it Works and Why it Matters - Brunner

A simulation project entails running **experiments**. Different experiments use different input data and/or model logic. Each experiment can consist of one or more **replications** or **trials**. A trial differs from an experiment because it uses the same model logic and input data, and thus only varies its set of random data.

The simulation **clock** is an internally managed and internally stored data value that is used to track the passage of time in a run. After all possible actions have been taken at a given simulated time (entity movement phase - **EMP**), the clock is advanced to the time of the next earliest event (clock update phase - **CUP**).

In general, simulations can execute interactively or in batch mode. Interactive mode is mostly used to verify the model’s logic and troubleshooting during development.

If the simulations being executed are large and can consume substantial amounts of time and compute resources, it might be useful to produce execution metrics of where most time is being spent in order to try and optimize the simulator. This seems more necessary in simulations where there are entities competing for resources, and wait queues, etc.

---

# Discrete-Event System Simulation - Fifth Edition - Banks et al

In some instances, a model can be simple enough to be implemented through mathematical methods. However, many real-world systems are too complex for this, and in these instances computer-based operations can be used to imitate the behavior of the system over time.

A **system** is a group of objects that are joined together in some regular interaction or interdependence toward achieving a goal. A system is often affected by changes occuring in the environment where it is placed. It is necessary to define the boundaries of what is part of the system and what is part of the external environment.

An **entity** is an object of interest in the system. An **attribute** is a property of an entity. An **activity** represents a period of time of known length. The **state** of the system is a collection of variables that describe the system at any time, and are defined according to the intended results that the simulation should produce. An **event** is an instantaneous occurrence that might change the state of the system.

System can be categorized as **discrete** (system state changes only at discrete points in time) or **continuous** (system state varies continuously over time). 

A **model** is a representation of a system. Usually, only the aspects of the system that are of interest for producing the required results are considered. The model is commonly a simplification of the system, however it should be detailed enough to allow for valid conclusions to be drawn about the real system.

Models can be **mathematical** or **physical**. A mathematical model uses symbolic notation and mathematical equations to represent a system. A physical model is a larger or smaller version of a real object/entity of a system.
These models can then be classified as **static** (represents the system at a particular point in time) or **dynamic** (represents systems as they change over time), **deterministic** (contains no random variables) or **stochastic** (one or more random variables as inputs, which leads to random outputs), and **discrete** or **continuous**.

A simulator can be **process oriented** or **event oriented**.
- ***Process Orientation*** - focus the program around the behavior of entities; may require threading mechanisms; programming is closer to real world programs; internally translated into a series of events without the programmer “noticing” it.
- ***Event Orientation*** - focus the program on the creation and processing of events.

*For our purposes, the simulator will definitely be event driven and that will be abstracted to provide process orientation to the user of the simulator. The user can write “usual” code like send(msg, dest) which internally gets converted into an event and handled as such.*

---