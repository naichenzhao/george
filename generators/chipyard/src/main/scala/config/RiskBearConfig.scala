package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.prci.{AsynchronousCrossing}
import freechips.rocketchip.subsystem.{InCluster}

import riskybear._

// --------------
// Rocket Configs
// --------------

class RiskyBearConfig extends Config(
  // We want to connect through UART TSI
  new chipyard.config.WithBroadcastManager ++ // no l2
  new chipyard.harness.WithSerialTLTiedOff ++

  // Add QDEC modules
  new WithQDEC(address = 0x11000000) ++
  new chipyard.harness.WithQDECTiedOff ++

  // Add Motor modules
  new WithMotor(address = 0x12000000) ++

  new chipyard.NoCoresConfig)



class RiskyBearRocketConfig extends Config(
  new WithRobotJoint() ++
  new chipyard.harness.WithJointsTiedOff ++

  new freechips.rocketchip.subsystem.WithoutTLMonitors ++

  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)
