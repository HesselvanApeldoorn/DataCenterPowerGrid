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

We implemented a system designed to allow individual servers in a data
center to adapt to changes in energy availability. We assume that the
relative abundance or scarcity of electrical energy is reflected in
the minute-to-minute price of electricity expressed in eurocents per
kWh, and that a single supplier sets this price. This implies some
loss of generality since in the real world there may be multiple
suppliers, each demanding a different price depending on the
conditions of their respective producers. Moreover the actual cost of
production depends on the amount produced - as more energy is required
by the grid, more expensive power sources will be used to meet the
demand. Such difficulties can be disregarded if we assume that energy
producers - which set the price - assume the (financial) risk of
having to tap into more expensive sources and that an energy broker
process negotiates the best possible deal between multiple energy
producers, thereby providing the clients of this system with a single
price. These negotiations are outside the scope of this project,
though.

The clients of this system are the servers that can adapt their energy
use individually. As servers in data centers are typically stacked in
a rack, and racks need to be cooled as a unit, it might make more
sense to adapt the energy use of a whole rack. In this case a client
represents an entire rack of such blade servers. Clients are assumed
to run jobs of varying economic value, and will change their energy
consumption with the price depending on the value of their job and the
job-specific relation between energy use and performanceb. For
example, CPU- or GPU-bound jobs will depend on CPU-performance (and
energy use) more than a web crawling job will, since that process is
likely to spend most time waiting for network requests. Thus each
client will have it's own schedule for energy use and economic value
and can adapt it's use accordingly. Note that with the development of
cloud-hosted computing systems such as amazon web services, many
'jobs'in real-world data centers really do have an economic value
assigned to them.



# Relationship with Distributed Systems

