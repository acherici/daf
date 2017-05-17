package it.gov.daf.injestionmanager

import it.teamDigitale.daf.schema.schemaMgmt.{ConvSchemaGetter, StdSchemaGetter, SchemaMgmt}

import it.teamDigitale.daf.injestion.DataInjCsv

import it.teamDigitale.daf.injestion.DataInjCsv

/**
  * Created by ale on 17/05/17.
  */
object Test {
   def test() = {
     val convSchema = ConvSchemaGetter.getSchema()

     println(convSchema)

     val stdSchema = new StdSchemaGetter("daf://dataset/vid/mobility/gtfs_agency").getSchema()
     println(stdSchema)

     convSchema match {
       case Some(s) =>
         val dataInj = new DataInjCsv(new SchemaMgmt(s))
         println(dataInj.doInj())
       case _ => println("ERROR")
     }
   }

}
