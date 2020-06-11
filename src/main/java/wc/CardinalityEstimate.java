package wc;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.*;

public class CardinalityEstimate extends Configured implements Tool {

    private static int  MAX_USER = 100;

    public static void main(String[] args) {
        if (args.length != 3) {
            throw new Error("Three arguments required:\n<input-dir> <output-dir>");
        }

        try {
            ToolRunner.run(new CardinalityEstimate(), args);
        } catch (final Exception e) {
            logger.error("", e);
        }
        System.out.println("running");
    }


    private static final Logger logger = LogManager.getLogger(CardinalityEstimate.class);

    public static class CardinalityMapper extends Mapper<Object, Text, Text, IntWritable> {
        private final static IntWritable one = new IntWritable(1);
        private final Text word = new Text();

        @Override
        public void setup(Context context){
            Configuration conf = context.getConfiguration();
            String p1 = conf.get("maxcount");
            CardinalityEstimate.MAX_USER = Integer.parseInt(p1);
        }

        @Override
        public void map(final Object key, final Text value, final Context context) throws IOException, InterruptedException {
            final StringTokenizer itr = new StringTokenizer(value.toString());
            while (itr.hasMoreTokens()) {
                String[] values = itr.nextToken().split(",");
                int userFrom = Integer.parseInt(values[0]);
                int userTo = Integer.parseInt(values[1]);
                if(userFrom < MAX_USER && userTo < MAX_USER){
                    word.set(""+userFrom + "-Outgoing");
                    context.write(word, new IntWritable(1));
                    word.set(""+userTo + "-Incoming");
                    context.write(word, new IntWritable(1));
                }

            }
        }
    }

    public static class CardinalityReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
        private final IntWritable result = new IntWritable();

        @Override
        public void reduce(final Text key, final Iterable<IntWritable> values, final Context context) throws IOException, InterruptedException {
            int sum = 0;
            for (final IntWritable val : values) {
                sum+=1;
            }
            result.set(sum);
            context.write(key, result);

        }
    }


    public static class CardinalityMergeMapper extends Mapper<Object, Text, Text, LongWritable> {
        private final static IntWritable one = new IntWritable(1);
        private final Text word = new Text();
        private Map<String,Integer> userIdToInfo = new HashMap<String, Integer>();
        @Override
        public void setup(Context context) throws IOException,InterruptedException {
            try {

                Configuration conf1 = context.getConfiguration();

                String p1 = conf1.get("data");
                FileSystem fs = FileSystem.get(URI.create(p1), conf1);
                FileStatus[] files = fs.listStatus(new Path(p1));

                for (FileStatus p : files) {
                    BufferedReader rdr = new BufferedReader(
                            new InputStreamReader(fs.open(p.getPath())));
                    String line;
                    // For each record in the user file
                    while ((line = rdr.readLine()) != null) {
                        // Get the user ID for this record
                        String[] temp = line.split(",");
                        int userTo = Integer.parseInt(temp[1]);
                        userIdToInfo.put(temp[0], userTo);
                        logger.info("----- map ---- "+ temp[0] + " --- " + userTo);
                    }
                }
            }catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void map(final Object key, final Text value, final Context context) throws IOException, InterruptedException {
            final StringTokenizer itr = new StringTokenizer(value.toString());
            Map<Integer, Integer> cardinality = new HashMap<>();
            while (itr.hasMoreTokens()) {
                String valueRead = itr.nextToken();
                String[] temp  = valueRead.toString().split(",");
                String userFrom = temp[0].split("-")[0];
                int userTo = Integer.parseInt(temp[1]);
                String incoming = userFrom + "-Incoming";
                String outgoing = userFrom + "-Outgoing";
                int m = userIdToInfo.get(incoming)== null ? 0:userIdToInfo.get(incoming);
                int n = userIdToInfo.get(outgoing) == null ? 0:userIdToInfo.get(outgoing);
                int path2 = m * n;
                context.write(new Text("Path"),new LongWritable(path2));


            }
        }
    }

    public static class CardinalityMergeReducer extends Reducer<Text, LongWritable, Text, LongWritable> {
        private final LongWritable result = new LongWritable();

        @Override
        public void reduce(final Text key, final Iterable<LongWritable> values, final Context context) throws IOException, InterruptedException {
            long sum = 0;
            for (final LongWritable val : values) {
                sum+=val.get();
            }

            // dividing by 2 because hashmap in mapper have both incoming and outgoing nodes separately
            // which are counted twice
            sum = sum/2;
            result.set(sum);
            logger.info("----- sum ---- " + sum);
            context.write(key, result);

        }
    }

    @Override
    public int run(final String[] args) throws Exception {
        final Configuration conf = getConf();
        conf.set("maxcount",""+Integer.parseInt(args[2]) );
        final Job job = Job.getInstance(conf, "Twitter Triangle Sample");
        job.setJarByClass(CardinalityEstimate.class);
        final Configuration jobConf = job.getConfiguration();
        jobConf.set("mapreduce.output.textoutputformat.separator", ",");
        job.setMapperClass(CardinalityMapper.class);
        job.setReducerClass(CardinalityReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path("filtered"));
        job.waitForCompletion(true);

        conf.set("data",new Path("filtered").toString());
        final Job job2 = Job.getInstance(conf, "Twitter Triangle");
        job2.setJarByClass(CardinalityEstimate.class);
        final Configuration jobConf2 = job2.getConfiguration();
        jobConf2.set("mapreduce.output.textoutputformat.separator", ",");
        job2.setMapperClass(CardinalityMergeMapper.class);
        job2.setReducerClass(CardinalityMergeReducer.class);
        job2.setOutputKeyClass(Text.class);
        job2.setOutputValueClass(LongWritable.class);
        FileInputFormat.addInputPath(job2, new Path("filtered"));
        FileOutputFormat.setOutputPath(job2, new Path(args[1]));

        return job2.waitForCompletion(true) ? 0 : 1;



    }



}