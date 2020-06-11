Hadoop MapReduce Twitter-Triangle Demo
Example code for CS6240
Spring 2020

Code author
-----------
Dharmish Shah

Installation
------------
These components are installed:
- JDK 1.8
- Hadoop 2.9.1
- Maven
- AWS CLI (for EMR execution)

Environment
-----------
1) Example ~/.bash_aliases:
export JAVA_HOME=/usr/lib/jvm/java-8-oracle
export HADOOP_HOME=/home/joe/tools/hadoop/hadoop-2.9.1
export YARN_CONF_DIR=$HADOOP_HOME/etc/hadoop
export PATH=$PATH:$HADOOP_HOME/bin:$HADOOP_HOME/sbin

2) Explicitly set JAVA_HOME in $HADOOP_HOME/etc/hadoop/hadoop-env.sh:
export JAVA_HOME=/usr/lib/jvm/java-8-oracle

Submission
--------
1) Unzip project file.
2) Navigate to directory where project files are unzipped.
3) Go to output folder.
4) The program was ran on AWS, 6 machine instance and 7 machine instance respectively for each Rep and Reduce Join
    with max user value = 10000 and 25000.
5) SysLog, controller and output file can be found for respective runs.

Execution
---------
All of the build & execution commands are organized in the Makefile.
1) Unzip project file.
2) Open command prompt.
3) Navigate to directory where project files unzipped.
4) Edit the Makefile to customize the environment at the top.
	Sufficient for standalone: hadoop.root, jar.name, local.input
	Other defaults acceptable for running standalone.
5) Standalone Hadoop:
	make switch-standalone		-- set standalone Hadoop environment (execute once)
	make local                  -- default runs replicated join on local
	make local-replicated       -- runs replicated join on local
	make local-reduce           -- runs reduce join on local
	make local-maxfilter        -- filters all user edges from input upto MAX_USER on local
	make local-cardinality      -- finds Path-2 cardinality based on MAX_USER value on local
6) Pseudo-Distributed Hadoop: (https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-common/SingleCluster.html#Pseudo-Distributed_Operation)
	make switch-pseudo			-- set pseudo-clustered Hadoop environment (execute once)
	make pseudo					-- first execution
	make pseudoq				-- later executions since namenode and datanode already running
7) AWS EMR Hadoop: (you must configure the emr.* config parameters at top of Makefile)
	make upload-input-aws		-- only before first execution
	make aws					-- default runs replicated join on aws
	make aws-reduce				-- runs reduce join on AWS
	make aws-replicated			-- runs replicated join on AWS
	make aws-maxfilter			-- filters all user edges from input file upto MAX_USER on AWS
	make aws-cardinality        -- finds Path-2 cardinality based on MAX_USER value on AWS
	download-output-aws			-- after successful execution & termination, download output