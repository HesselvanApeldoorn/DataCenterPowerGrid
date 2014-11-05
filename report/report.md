# Context

It is an unfortunate fact of electrical energy that it's production
and consumption must be balanced at all times. Since the consumption
of energy varies throughout the day as people and businesses employ
machines and appliances network managers have always relied on various
strategies to adapt production to the demand. The main strategy is to
have a diverse array of power plants, some which can be started and
stopped quickly (such as hydroelectrical and gas-fired power plants),
and some which cannot adapt quickly but which are more economical in
operation, such as coal-fired and nuclear power plants.

This strategy relies heavily on the use of fossil fuels, whose output
is predictable. This poses new challenges in the coming century as
fossil fuels become increasingly scarce and expensive. Moreover, the
continued use of fossil fuels is likely to cause dangerous global
climate change which, when it occurs, is certain to have large and
disastrous impacts on natural ecosystems as well as human habitats and
the economy. So to continue to use electrical energy - as any modern
economy must - other, renewable resources need to be developed. Chief
among these are wind energy and photovoltaic solar energy. 

However, unlike fossil fuels the production of these energy from
renewable sources cannot be adapted to meet demand. Energy is produced
whenever the wind blows and the sun shines, even if we'd rather use it
at any other time. At the same time energy production cannot be
increased at times of great demand (such as during important sporting
events). So the use of renewable energy brings about new challenges
for the managers of the electricity grid. It is believed that this
challenge can be met only by the implementation of so-called 'smart
grid' systems.

The key idea of a smart grid is that the electricity consumers are
made aware of and can react to (sudden) changes in the production
capacity of the electricity grid. Just how such systems adapt to 
energy scarcity and abundance depends on the system. For example, a
refrigerator which stays cool for a long time may switch off cooling
at times of scarcity and compensate when electricity is
abundant. For another example, modern computer systems contain
sophisticated power management systems which allow them to vary energy
use and performance nearly instantaneously. This adaptive capability
may prove very valuable in combination with a smart grid system.

# Problem Statement

As the use of computing in modern societies has grown, so has the
energy use devoted to computation. In 2010 this was measured to be 1%
of total human energy consumption [1]. More significantly, in the
period between 2005 and 2010 the energy used by computing doubled,
growing by 16,7% each year [1]. With the development of cloud
computing, an increasing proportion of this energy is used in data
centers. Given these trends, it becomes important to manage the energy
consumption by data centers. Fortunately, because of the
aforementioned sophisticated power management systems, this is
practically doable.

We implemented a system designed to allow individual energy consumers
(i.e. servers) in a data center to adapt to changes in energy
availability. We assume that the relative abundance or scarcity of
electrical energy is reflected in the minute-to-minute price of
electricity expressed in eurocents per kWh, and that a single supplier
sets this price. This implies some loss of generality since in the
real world there may be multiple suppliers, each demanding a different
price depending on the conditions of their respective
producers. Moreover the actual cost of production depends on the
amount produced - as more energy is required by the grid, more
expensive power sources will be used to meet the demand. Such
difficulties can be disregarded if we assume that energy producers -
which set the price - assume the (financial) risk of having to tap
into more expensive sources and that an energy broker process
negotiates the best possible deal between multiple energy producers,
thereby providing the clients of this system with a single
price. These negotiations between broker and producers are outside the
scope of this project, though.

The clients of this system are the servers that can adapt their energy
use individually. As servers in data centers are typically stacked in
a rack, and racks need to be cooled as a unit, it might make more
sense to adapt the energy use of a whole rack. In this case a client
represents an entire rack of such blade servers. Clients are assumed
to run jobs of varying economic value, and will change their energy
consumption with the price depending on the value of their job and the
job-specific relation between energy use and performance. For
example, computationally intensive jobs will depend on CPU-performance
(and energy use) more than a web crawling job will, since that process
is likely to spend most time waiting for network requests. Thus each
client will have it's own schedule for energy use and economic value
and can adapt it's use accordingly. Note that with the development of
cloud-hosted computing systems such as amazon web services, many
'jobs' in real-world data centers really do have an economic value
assigned to them.


# Relation to Distributed Systems

Throughout our entire smart energy grid for data centers many 
techniques from the field of distributed aystems are used. Even the 
data center itself can be considered as a distributed system. Since
it consists of several nodes all connected with each other through a
network.

The most used technique is multicast, specifically ordered reliable IP multicast. 
Nodes in a group have to reach concensus on the energy price together
with the broker. In order to achieve this, the broker sends out a
message using IP multicast to let nodes know the price of energy
for a certain amount of energy for a certain amount of time. Nodes
then try to come up with the amount of energy that they would like
to buy. This is then multicast across all nodes for replication
of the bought amounts.

Nodes are not always on. Whenever a node has nothing to do it may not be powered and connected to the network. We use the distributed system technique dynamic host discovery to ensure that nodes can properly join the energy grid when they do need to do computations. A node knows which group it wants to join. It sends a message to the leader that it wants to join that particular group. The leader then acknowledges this join request and the entire group is notified of a new node entering the group.

Furthermore, it's also possible that nodes leave, including broker nodes that fulfill a leading position. In this case we need to assign a new broker. This is done by using the technique leader election. As soon as a node notices that the broker is down, a node will start an election given that the node itself can become the new broker. The election variant that we use in our energy grid is the Bully algorithm.

It may happen that nodes in the smart energy grid crash. This can occur due to hardware failure or human error, for example. There is thus the need to be able to solve crash faults as well as omission faults. In our project crash faults are solved by sending heartbeats. Which means that for every small period of time, a message is send to a node to check if it's still alive. Omission faults are present in sending messages to just one other node or multicasting it to all nodes within a group. In case of a failure of sending to one node, the node requests a resend of the message to the sender. In case of missing a multicast message, the entire group is asked to resend the message to the node that's missing a message.


# Details of the technical implementation

Many aspects of our system are based on the raft consensus protocol
[2]. However, there are several important differences. The main
difference is that we use asynchronous IP multicast to deliver
messages to the group of computers, whereas raft uses peer-to-peer
remote procedure calls. This was done because raft is designed for a
relatively small system (a clustor of 5), whereas our system is
intended to serve a far greater number of clients. Most of the further
differences from raft follow from this decision.

# Reliable ordered messaging

Each process acquires a socket for receiving multicast messages, and a
socket for receiving peer-to-peer messages. Both are unreliable,
asynchronous UDP sockets. All messages are sent through the
peer-to-peer socket, even those destined for the multicast group. This
is possible due to the open nature of IP multicast, which allows any
sender to send messages. As a result, messages send by any process to
the multicast group are also delivered to that same process. This
property simplifies the design because there are fewer exceptions.

To ensure reliable delivery of peer-to-peer messages, all sent
messages are kept in a buffer and need to be acknowledged by the
receiver. Until they are acknowledged they will be sent periodically,
stopping only when the failure of the receiver has been
reported. Sequence numbers are used to distinguish different messages
and to ensure ordering.

Multicast delivery on the other hand uses negative acknowledgements,
or _resend requests_, to ensure eventual delivery to all hosts. Just
as for peer-to-peer messages, a process issues sequence number on each
multicast message it sends. On the receivers' end, this sequence
number is used to ensure ordering using a holdback queue. Using only
this sequence number, a receiving process guarantees delivery in
sender order, but no more. When a process receives an out-of-order
message, it is put into the holdback queue but not delivered.
Periodically the process checks for gaps in the multicast holdback
queue and sends a resend request. If the original sender is still
alive, the request is sent directly to this host, which will
retransmit the message. If the host has failed, the request is sent to
the group. Whenever a multicast message has been delivered, a process
retains it. So if any host has delivered a given multicast message,
all other hosts can still request retransmission if the original
sender has died.

# Bootstrapping and Leader election



# Host discovery and failure detection



# Failure modes

