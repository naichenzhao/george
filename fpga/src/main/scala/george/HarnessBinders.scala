package chipyard.fpga.george

import chisel3._

import freechips.rocketchip.jtag.{JTAGIO}
import freechips.rocketchip.subsystem.{PeripheryBusKey}
import freechips.rocketchip.tilelink.{TLBundle}
import freechips.rocketchip.diplomacy.{LazyRawModuleImp}
import org.chipsalliance.diplomacy.nodes.{HeterogeneousBag}
import sifive.blocks.devices.uart.{UARTPortIO, UARTParams}
import sifive.blocks.devices.jtag.{JTAGPins, JTAGPinsFromPort}
import sifive.blocks.devices.pinctrl.{BasePin}
import sifive.fpgashells.shell._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.clocks._
import chipyard._
import chipyard.harness._
import chipyard.iobinders._
import testchipip.serdes._
import testchipip.serdes.old._

class WithGeorgeFPGAUARTTSI extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTTSIPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[GeorgeFPGAHarness]
    val harnessIO = IO(new UARTPortIO(port.io.uartParams)).suggestName("uart_tsi")
    harnessIO <> port.io.uart
    val packagePinsWithPackageIOs = Seq(
      ("D10" , IOPin(harnessIO.rxd)),
      ("A9", IOPin(harnessIO.txd)))

    packagePinsWithPackageIOs foreach { case (pin, io) => {
      ath.xdc.addPackagePin(io, pin)
      ath.xdc.addIOStandard(io, "LVCMOS33")
      ath.xdc.addIOB(io)
    } }
  }
})

class WithGeorgeFPGATDDRTL extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: TLMemPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[GeorgeFPGAHarness]
    val bundles = ath.ddrClient.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> port.io
  }
})

class WithGeorgeFPGASerialTLToGPIO extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: OldSerialTLPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[GeorgeFPGAHarness]
    val harnessIO = IO(chiselTypeOf(port.io)).suggestName(s"serial_tl_old_${port.portId}")
    harnessIO <> port.io
    harnessIO match {
      case io: testchipip.serdes.old.DecoupledSerialIO => {
        val clkIO = io match {
          case io: testchipip.serdes.old.InternalSyncSerialIO => IOPin(io.clock_out)
          case io: testchipip.serdes.old.ExternalSyncSerialIO => IOPin(io.clock_in)
        }

        val packagePinsWithPackageIOs = if (port.portId == 0) {
          Seq(
            ("J17", clkIO),

            ("D15", IOPin(io.in.valid)),
            ("E16", IOPin(io.in.ready)),
            ("C16", IOPin(io.in.bits, 0)),
            ("A18", IOPin(io.in.bits, 1)),
            ("F18", IOPin(io.in.bits, 2)),
            ("H17", IOPin(io.in.bits, 3)),
            ("B18", IOPin(io.in.bits, 4)),
            ("E17", IOPin(io.in.bits, 5)),
            ("A15", IOPin(io.in.bits, 6)),
            ("B16", IOPin(io.in.bits, 7)),

            ("J18", IOPin(io.out.valid)),
            ("G17", IOPin(io.out.ready)),
            ("E18", IOPin(io.out.bits, 0)),
            ("C15", IOPin(io.out.bits, 1)),
            ("D18", IOPin(io.out.bits, 2)),
            ("G18", IOPin(io.out.bits, 3)),
            ("C17", IOPin(io.out.bits, 4)),
            ("D17", IOPin(io.out.bits, 5)),
            ("C14", IOPin(io.out.bits, 6)),
            ("B17", IOPin(io.out.bits, 7)),
          )
        } else {
          Seq(
            ("D12", clkIO),
            ("B11", IOPin(io.out.valid)),
            ("A13", IOPin(io.out.ready)),
            ("B12", IOPin(io.out.bits, 0)),
            ("A14", IOPin(io.in.valid)),
            ("A11", IOPin(io.in.ready)),
            ("C12", IOPin(io.in.bits, 0)),
          )
        }
        packagePinsWithPackageIOs foreach { case (pin, io) => {
          ath.xdc.addPackagePin(io, pin)
          ath.xdc.addIOStandard(io, "LVCMOS12")
        }}

        // // Don't add IOB to the clock, if its an input
        // io match {
        //   case io: testchipip.serdes.old.InternalSyncSerialIO => packagePinsWithPackageIOs foreach { case (pin, io) => {
        //     ath.xdc.addIOB(io)
        //   }}
        //   case io: testchipip.serdes.old.ExternalSyncSerialIO => packagePinsWithPackageIOs.drop(1).foreach { case (pin, io) => {
        //     ath.xdc.addIOB(io)
        //   }}
        // }

        ath.sdc.addClock("ser_tl_clock", clkIO, 100)
        ath.sdc.addGroup(pins = Seq(clkIO))
        ath.xdc.clockDedicatedRouteFalse(clkIO)
      }
    }
  }
})

class WithGeorgeJoints() extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: RobotJointPort, chipId: Int) => {

    // Imma just hard-code the pins here cause im cringe
    val motor_a = Seq("E3", "D2", "F3", "G2", "B4", "C5", "B7", "D7")
    val motor_b = Seq("C2", "B1", "G3", "E1", "A5", "C4", "C7", "E7")
    val qdec_a =  Seq("A4", "D3", "C1", "E2", "A6", "G6", "E5", "E6")
    val qdec_b =  Seq("A3", "D4", "A1", "F1", "F4", "G4", "F5", "D5")

    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[GeorgeFPGAHarness]
    val harnessIO = IO(chiselTypeOf(port.io)).suggestName(s"robot_joints_${port.pinId}")
    harnessIO <> port.io
    val packagePinsWithPackageIOs = Seq(
      (motor_a(port.pinId), IOPin(harnessIO.motor_out_a)),
      (motor_b(port.pinId), IOPin(harnessIO.motor_out_b)), 
      (qdec_a(port.pinId), IOPin(harnessIO.qdec_a)),
      (qdec_b(port.pinId), IOPin(harnessIO.qdec_b)))
      
    packagePinsWithPackageIOs foreach { case (pin, io) => {
      ath.xdc.addPackagePin(io, pin)
      ath.xdc.addIOStandard(io, "LVCMOS33")
      ath.xdc.addPullup(io)
      ath.xdc.addIOB(io)
    } }
  }
})


