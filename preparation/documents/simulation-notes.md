# Table of contents
1. [Introduction to Simulation](#simintro)
2. [Agent-Based Framework for Modeling Blockchains](#agentbased)
3. [Inside Discrete-Event Simulation Software](#disceventsoftware)
4. [Modeling and Tools for Network Simulation](#modtoolssim)
	1. [ns3 Simulator](#ns3)
	2. [OMNeT++ Simulator](#omnet)
	3. [Modeling the Internet Delay Space in P2P Simulations](#p2pdelay)
	4. [Modeling User Behavior in P2P Systems](#p2puserbehavior)
5. [Virtual-Time-Accelerated Emulation for Blockchain Networks](#virtualtimeemulation)
6. [Architecture of Existing Blockchain Simulators](#bcarchitecture)

---

# Introduction to Simulation - Jerry Banks <a name="simintro"></a>

## Winter Simulation Conference (Rank B)

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

# A Generalized Agent Based Framework for Modeling a Blockchain System - Macal <a name="agentbased"></a>

## Winter Simulation Conference (Rank B)

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

# Inside Discrete-Event Simulation Software: How it Works and Why it Matters - Brunner <a name="disceventsoftware"></a>

## Winter Simulation Conference (Rank B)

A simulation project entails running **experiments**. Different experiments use different input data and/or model logic. Each experiment can consist of one or more **replications** or **trials**. A trial differs from an experiment because it uses the same model logic and input data, and thus only varies its set of random data.

The simulation **clock** is an internally managed and internally stored data value that is used to track the passage of time in a run. After all possible actions have been taken at a given simulated time (entity movement phase - **EMP**), the clock is advanced to the time of the next earliest event (clock update phase - **CUP**).

In general, simulations can execute interactively or in batch mode. Interactive mode is mostly used to verify the model’s logic and troubleshooting during development.

If the simulations being executed are large and can consume substantial amounts of time and compute resources, it might be useful to produce execution metrics of where most time is being spent in order to try and optimize the simulator. This seems more necessary in simulations where there are entities competing for resources, and wait queues, etc.

---

# Modeling and Tools for Network Simulation <a name="modtoolssim"></a>

## Book with ~300 citations, from 2010

#### Author/Editor has ~9000 citations and an h-index of 43
#### Different chapters have contributions of different authors

When explaining discrete-event simulation, the authors reference the previously read books: "Simulation Modeling and Analysis" by Avrill Law and "Discrete Event System Simulation" by Jerry Banks.

Simulation runs can be classified into *transient* and *steady state* simulations. A transient simulation is also referred to as *terminating* simulation, and it rusn for a pre-defined period of time (possesses a termination condition). A steady state simulation is also referred to as *non-terminating* simulation, and the goal of this type of simulation is to study the long-term behavior of the system.

Due to its high level of *abstraction*, *flexibility* and *scalability* network simulation is the standard means for evaluation of distributed systems. Its abstraction from implementation details such as target platforms, operating systems and devices limit the impact of system artifacts and allows a researcher to solely focus on algorithmic challenges.

### The ns-3 Simulator <a name="ns3"></a>

NS-3 is a simulator developed in C++, where users can execute network simulations by writing C++ or Python programs.

It offers models for various network elements that comprise a computer network:

- **network nodes**: represent computers, routers, hubs and switches.
- **network devices**: represents a physical device that connects a node to a communications channel (ie. network card).
- **communication channels**: represent the medium used to send information between network devices (point-to-point links, ethernet, etc.)
- **communication protocols**: model the implementation of several protocol descriptions. Are organized into a protocol stack, where each layer in the stack performs some specific and limited function on network packets, and then passes the packet to another layer for more processing, etc.
- **network packets**: the unit of information exchange in computer networks. Contains protocol headers and (optionally) payload.

The simulator also offers helper objects to assist in the execution and analysis of the simulation:

- Creation of random variables and observations of probability distributions.
- Trace objects that facilitate the logging of performance data during the execution of the simulation.
- Helpers to assist and hide some details of creating the simulations (for example, helper for creating point-to-point networks).

In writing the simulation program, the following steps are performed:

- create the *network topology*, by instantiating all the nodes, devices, channels and network protocols that are being modeled.
- create the *data demand* on the network, by creating models of various applications that send and receive information using the network, leading to packet creation, receival and processing.

### The OMNeT++ Simulator <a name="omnet"></a>

Provides a generic *component architecture* that users can take advantage of to map concepts such as network devices, protocols, channels, etc. These components are termed *modules* and can be combined in several ways. Modules communicate via message passing, which can represent events, packets, commands, jobs, etc.

The simulator provides:

- a C++ kernel and class library to build the different modules.
- infrastructure to assemble simulations from these components and to configure them.
- graphical and batch mode simulation runtime interfaces.
- custom IDE for designing, running and evaluating simulations.
- extensions for real-time simulation, emulation, parallel distributed simulation, etc.
- message and event handling mechanisms.
- random number generation and various distributions.
- publish-subscribe style mechanisms.
- utility classes for queues, statistics, discovery and routing, etc.
- a domain specific language called NED, for module declaration and network definitions.


### Modeling the Internet Delay Space in Large Scale P2P Simulations <a name="p2pdelay"></a>

#### Authors citations (~1000 and ~3500)

Main challenges in creating an Internet delay space model:

- the model must be able to predict lifelike delays and jitter between a given pair of end-hosts.
- the computation of delays must scale with respect to time.

The minimal end-to-end delay between two hosts is limited by the propagation speed of signals in the involved links which increases proportionally with the link length. 

The state of the Internet infrastructure varies significantly in different countries. Jitter and packet loss rates are heavily influenced by the location of the participating nodes.

Approaches to obtain an Internet delay model:

- **Analytical function**: delay is computed by an analytical function that uses as an input the distance between any two hosts. Simple run-time computations and no memory overhead, but neglects the geographical distribution and location of hosts on earth, which are needed in order to model lifelike delays and jitter.
- **King method**: compute the all-pair end-to-end delays among a large number of globally distributed DNS servers. Limited by the amount of time necessary to collect a decent amount of data, sometimes countered by the use of a delay synthesizer, however this neglects delay variation.
- **Topology generators**: use artificial link delays assigned by topology generators. Generates a topology file for a predefined number of nodes *n*. Calculates the delays using the generated topology file.
- **Euclidean embedding**: use the data gathered by projects such as Surveyor, CAIDA and AMP, all freely available. These projects proble millions of hosts from a small number of globally distributed monitor hosts. This data is then used as an input to generate realistic delay by embedding hosts into a multi-dimensional Euclidean space.

| Model                              | Computation Cost | Memory Overhead | Comment                                                          |
|------------------------------------|------------------|-----------------|------------------------------------------------------------------|
| Analytical Function                | low              | O(1)            | static delays; neglects georaphical position                     |
| King Method                        | low              | O(n²)           | static delays; very high precision; complicated data acquisition |
| Topology Generators (pre-computed) | low              | O(n²)           | static delays; neglects geographical position                    |
| Topology Generators (on-demand)    | very high        | low             | static delays; neglects georaphical position                     |
| Euclidean Embedding                | low              | O(n)            | data freely available                                            |


However, all these approaches seem to neglect the delay and jitter caused by specific geographical locations of the nodes.

An alternative solution is then presented in **Chapter 19.5**.


### Modeling User Behavior in P2P Systems <a name="p2puserbehavior"></a>

#### Authors citations (~1000, ~600)

The behavior of P2P users is rather complex. The main components are *churn*, *workload* and *user properties*.

- **Churn**:
	- describes the participation dynamics of P2P nodes. Users join the network, leave, and come back multiples times. Other times the user does not come back. The churn consists of the *lifetime* of a node, encompassing several online and offline cycles. Each online cycle is called a *session*.
	- joining and leaving are system-specific operations. Normally, joining includes finding neighbors, initializing routing tables, replicating data, etc. Leaving cleans up the session state. Some nodes don't leave normally, they can simply *crash*.
- **Workload**:
	- a node can be both a server and a clinet. The provision and consumption of resources specifies the *workload* of the system. A realistic workload model is crucial for evaluation.
- **User Properties**:
	- both the *churn* and the *workload* operate directly on the simulated system which itself relies on an underlay model of the Internet in the simulator framework.
	- *user properties* interacts with the *churn* and *workload* components. These include the goals and interests of the user.



---


# Virtual-Time-Accelerated Emulation for Blockchain Network and Application Evaluation <a name="virtualtimeemulation"></a>

## Conference on Principles of Advanced Discrete Event Simulation (PADS) (Rank B) (Year 2019)

A container-based emulator, with a configurable network layer. Utilizes a time-advance mechanism to lessen the performance impact that the mining process would have on the execution of multiple containerized nodes in the same machine.

Identifies KEPIs (Key Emulation Performance Indicators), and their design goal is the fidelity of those KEPIs:

- block interval
- transaction throughput
- network throughput
- end-to-end latency
- block distribution (if the rewards a miner obtains are proportional to its computational resources)

The system is divided into the *blockchain application layer* and *network layer*. 

The authors implement virtual time to reduce computational resource usage and increase blockchain convergence speed when experimenting with PoW-based blockchain applications. The virtual time adjusts the difficulty to a smaller value, which the authors show has little impact in the fidelity of the emulation results.

### Networking Impact on Selfish Mining

As a case study, the authors use their emulator to show the impact that a network can have on selfish mining attacks. 

The results show that even if a miner (or group of miners) possesses 45% of the network's mining power (as opposed to the usual 51% required for an attack of this kind), if a network incurs in high delays and variance it is possible for a miner with less than 50% of computation power to monopolize the blockchain.


---


# Building a Blockchain Simulation using the Idris Programming Language

## Paper from 2019, less than 10 citations. One of the authors has ~9500 citations.

### (https://dl.acm.org/doi/pdf/10.1145/3299815.3314456)

The authors implement a very simplified version of a proof of work blockchain, using a functional programming language called Idris.

Altough their goal was **not** blockchain fidelity nor a way to study blockchain protocols, but more an overview of the language's functionalities, it might still be interesting to look at the source code of their implementation simply because they use a functional programming paradigm (https://github.com/sciadopitys/Idris-Blockchain).



---

# Overview of Architecture of other Blockchain Simulators <a name="bcarchitecture"></a>

## BlockSim: Blockchain Simulator

Components:

- Simulation World: manages the inputs of the simulator, such as configuration (message size, block size, possible node locations), delays (time taken to validate transactions, blocks, time between blocks), latency (latency between possible node locations) and throughput (received and sent throughput of possible node locations)
- Discrete Event Simulation Engine: scheduling of events, queuing and processing of events, communication between other components, management of simulation clock.
- Node Factory: creation and initialization of nodes, in user specified locations, etc.
- Transaction Factory: creates batches of random transactions that are then broadcasted by a random (or chosen) node.
- Reports: writing logs and metrics to files.
- Monitor: captures metrics such as number of blocks created per node, transactions broadcast per node, propagation time of transactions, etc.

## VIBES

Components:
- Node: each node is an Actor, and follows a very simple protocol that replicates the behavior in a blockchain network.
- Coordinator: essentially the same as the discrete event simulation engine in **BlockSim**.
- Reducer: takes the network's state as input, and returns an output in a convenient format to be processed by the client (visual interface).








