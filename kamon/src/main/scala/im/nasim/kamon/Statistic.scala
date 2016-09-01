package im.nasim.kamon

/**
  * Created by mma on 9/1/16.
  */
class Statistic {


}

class CPUStatistic(var user:PacketData, var system: PacketData, var waitParam:PacketData, var idle:PacketData) extends Statistic {

}

class NetStatistic(var RxBytes : PacketData, var TxBytes : PacketData, var RxErrors : Long, TxError : Long ) extends Statistic {

}

class ProcessCPUStatistic(var userPercentage : PacketData, var totalPercentage : PacketData) extends Statistic {

}

class SwitchStatistic(var global : PacketData, var  perProcessNonVoluntary : PacketData, var perProcessVoluntary : PacketData) extends Statistic {

}

class PacketData(val min : Double, val average : Double, val max : Double) {

}

