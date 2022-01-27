# InfluxExporterJson
Export influx data to json  
Part of [data collector](https://github.com/suhoy/TestUploader) for [load testing hub](https://github.com/suhoy/cms-boot)   
Works with influx v1.8 and java 8   

### Arguments
```java
-config     Path to config file  
-times      Start of time periods, from 1 to N  
-durations  Periods durations, could be 1 or N  
-profiles   Profiles in %, could be 1 or N  
-out        Path to output data (absolute or relative) 
-name       Output file name (date and time will be added)  
```

### Start example
```java
java -jar InfluxExporterJson-1.0.jar -config C:\example\config.txt -out C:\example\out -times 2022-01-12T12:00:00 -durations 24:00:00 -profiles 80
or
java -jar InfluxExporterJson-1.0.jar -config config.txt -out .\out -name example2 -times 2022-01-12T20:55:20 2022-01-12T21:01:20 -durations 00:05:00 -profiles 100 200
or  
java -jar InfluxExporterJson-1.0.jar -config config.txt -out .\out -name example3 -times 2022-01-10T23:55:00 2022-01-11T23:55:00 2022-01-12T23:55:00 -durations 00:30:00 01:00:00 01:30:00 -profiles 150.5
```  
  
  
### Config example  
```properties
#connection data
influx.url=http://localhost:8086
influx.user=admin
influx.password=admin
influx.database=dbname


sql.count=4
#sql pass count
sql1.type=count
sql1.query=SELECT __function__ as "__column__" FROM "dbname"."autogen"."tablename" WHERE "status" = 'true' and  time > '__start__' AND time < '__finish__' GROUP BY "__script__"
sql1.column=pass_count
sql1.script=script

#sql fail count
sql2.type=count
sql2.query=SELECT __function__ as "__column__" FROM "dbname"."autogen"."tablename" WHERE "status" = 'false' and  time > '__start__' AND time < '__finish__' GROUP BY "__script__"
sql2.column=fail_count
sql2.script=script

#sql pass time
sql3.type=time
sql3.query=SELECT __function__ as "__column__" FROM "dbname"."autogen"."tablename" WHERE "status" = 'true' and  time > '__start__' AND time < '__finish__' GROUP BY "__script__"
sql3.column=pass_time
sql3.script=script

#sql fail time
sql4.type=time
sql4.query=SELECT __function__ as "__column__" FROM "dbname"."autogen"."tablename" WHERE "status" = 'false' and  time > '__start__' AND time < '__finish__' GROUP BY "__script__"
sql4.column=fail_time
sql4.script=script

#tags
tags.count=2
tag1.name=99.5% времен отклика
tag1.function.count=count("count")
tag1.function.time=(percentile("resp", 99.5))/1000

tag2.name=99.9% времен отклика
tag2.function.count=count("count")
tag2.function.time=(percentile("resp", 99.9))/1000


#profile in tps 100%, scripts names should be unique
sript1name.profile=2
sript1name.sla=1.5

sript2name123.profile=4
sript2name123.sla=2.5
```

### Output example
```json
[
	{
		"script": "sript1name",
		"tag": "Период 20:55 12.01.22 - 21:00 12.01.22 , 99.5% времен отклика",
		"pass_count": 478,
		"sla": 1.5,
		"profile": 600,
		"fail_count": 120,
		"pass_time": 1.867,
		"fail_time": 1.827
	},
	{
		"script": "sript2name123",
		"tag": "Период 20:55 12.01.22 - 21:00 12.01.22 , 99.5% времен отклика",
		"pass_count": 1199,
		"sla": 2.5,
		"profile": 1200,
		"pass_time": 2.499
	},
	{
		"script": "sript1name",
		"tag": "Период 20:55 12.01.22 - 21:00 12.01.22 , 99.9% времен отклика",
		"pass_count": 478,
		"sla": 1.5,
		"profile": 600,
		"fail_count": 120,
		"pass_time": 1.992,
		"fail_time": 1.957
	},
	{
		"script": "sript2name123",
		"tag": "Период 20:55 12.01.22 - 21:00 12.01.22 , 99.9% времен отклика",
		"pass_count": 1199,
		"sla": 2.5,
		"profile": 1200,
		"pass_time": 2.5
	},
	{
		"script": "sript1name",
		"tag": "Период 21:01 12.01.22 - 21:06 12.01.22 , 99.5% времен отклика",
		"pass_count": 987,
		"sla": 1.5,
		"profile": 1200,
		"fail_count": 210,
		"pass_time": 1.861,
		"fail_time": 1.738
	},
	{
		"script": "sript2name123",
		"tag": "Период 21:01 12.01.22 - 21:06 12.01.22 , 99.5% времен отклика",
		"pass_count": 2403,
		"sla": 2.5,
		"profile": 2400,
		"pass_time": 2.498
	},
	{
		"script": "sript1name",
		"tag": "Период 21:01 12.01.22 - 21:06 12.01.22 , 99.9% времен отклика",
		"pass_count": 987,
		"sla": 1.5,
		"profile": 1200,
		"fail_count": 210,
		"pass_time": 2.319,
		"fail_time": 2.218
	},
	{
		"script": "sript2name123",
		"tag": "Период 21:01 12.01.22 - 21:06 12.01.22 , 99.9% времен отклика",
		"pass_count": 2403,
		"sla": 2.5,
		"profile": 2400,
		"pass_time": 2.5
	}
]
```
