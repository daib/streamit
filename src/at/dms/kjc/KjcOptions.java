// Generated by optgen from KjcOptions.opt
package at.dms.kjc;

import at.dms.compiler.getopt.Getopt;
import at.dms.compiler.getopt.LongOpt;

public class KjcOptions extends at.dms.util.Options {

  public KjcOptions(String name) {
    super(name);
  }

  public KjcOptions() {
    this("Kjc");
  }
  public static boolean beautify = false;
  public static boolean help = false;
  public static boolean verbose = false;
  public static boolean java = false;
  public static String encoding = null;
  public static boolean nowrite = false;
  public static int warning = 1;
  public static boolean nowarn = false;
  public static boolean multi = false;
  public static boolean deprecation = false;
  public static int proc = 2;
  public static String destination = null;
  public static String classpath = null;
  public static String lang = "1.1";
  public static String filter = "at.dms.kjc.DefaultFilter";
  public static String output = null;
  public static boolean havefftw = false;
  public static boolean countops = false;
  public static boolean profile = false;
  public static String optfile = null;
  public static boolean stats = false;
  public static boolean print_partitioned_source = false;
  public static boolean debug = false;
  public static boolean fusion = false;
  public static boolean blender = false;
  public static boolean mencoder = false;
  public static boolean compressed = false;
  public static int frameheight = 720;
  public static int framewidth = 480;
  public static int unroll = 0;
  public static boolean partition_dp = false;
  public static boolean partition_ilp = false;
  public static boolean partition_greedy = false;
  public static boolean partition_greedier = false;
  public static boolean linearpartition = false;
  public static boolean dpscaling = false;
  public static boolean forceunroll = false;
  public static boolean decoupled = false;
  public static boolean sync = false;
  public static boolean ratematch = false;
  public static boolean manuallayout = false;
  public static boolean linearanalysis = false;
  public static boolean statespace = false;
  public static boolean nodatacollapse = false;
  public static boolean nolinearcollapse = false;
  public static boolean linearreplacement = false;
  public static boolean linearreplacement2 = false;
  public static boolean linearreplacement3 = false;
  public static boolean atlas = false;
  public static boolean frequencyreplacement = false;
  public static boolean redundantreplacement = false;
  public static boolean removeglobals = false;
  public static boolean poptopeek = false;
  public static int cluster = -1;
  public static int e2 = -1;
  public static int newSimple = -1;
  public static int vectorize = -1;
  public static boolean cell_vector_library = false;
  public static boolean cacheopt = false;
  public static int l1i = 8;
  public static int l1d = 8;
  public static int l2 = 256;
  public static int peekratio = -1;
  public static boolean nomult = false;
  public static boolean dynamicRatesEverywhere = false;
  public static int raw = -1;
  public static int rawcol = -1;
  public static boolean streamit = false;
  public static int numbers = -1;
  public static boolean standalone = false;
  public static boolean fixseed = false;
  public static boolean magic_net = false;
  public static boolean sjtopipe = false;
  public static boolean simulatework = false;
  public static boolean clone_with_serialization = false;
  public static boolean graph = false;
  public static boolean wbs = false;
  public static boolean spacetime = false;
  public static int cell = -1;
  public static boolean celldyn = false;
  public static int fission = -1;
  public static boolean destroyfieldarray = false;
  public static int outputs = -1;
  public static int iterations = -1;
  public static boolean struct = false;
  public static String backend = null;
  public static int steadymult = 1;
  public static boolean rstream = false;
  public static boolean doloops = false;
  public static boolean absarray = false;
  public static boolean modfusion = false;
  public static boolean spacedynamic = false;
  public static boolean space = false;
  public static boolean malloczeros = false;
  public static boolean macros = false;
  public static boolean nopartition = false;
  public static String layoutfile = null;
  public static String devassignfile = null;
  public static String backendvalues = null;
  public static int slicethresh = 101;
  public static boolean asciifileio = false;
  public static int ssoutputs = -1;
  public static boolean noswpipe = false;
  public static int st_cyc_per_wd = 1;
  public static boolean hwic = false;
  public static int dup = -1;
  public static boolean noswitchcomp = false;
  public static boolean autoparams = false;
  public static boolean greedysched = false;
  public static boolean workestunroll = false;
  public static int rawdcachesize = 32768;
  public static int rawicachesize = 32768;
  public static boolean localstoglobals = false;
  public static boolean checkpointSIR = false;
  public static boolean nompops = false;
  public static boolean lime = false;
  public static boolean simulator = false;

  public boolean processOption(int code, Getopt g) {
    switch (code) {
    case -2:
      beautify = !false; return true;
    case -3:
      help = !false; return true;
    case -4:
      verbose = !false; return true;
    case -5:
      java = !false; return true;
    case -6:
      encoding = getString(g, ""); return true;
    case -7:
      nowrite = !false; return true;
    case -8:
      warning = getInt(g, 2); return true;
    case -9:
      nowarn = !false; return true;
    case -10:
      multi = !false; return true;
    case -11:
      deprecation = !false; return true;
    case -12:
      proc = getInt(g, 0); return true;
    case -13:
      destination = getString(g, ""); return true;
    case -14:
      classpath = getString(g, ""); return true;
    case -15:
      lang = getString(g, ""); return true;
    case -16:
      filter = getString(g, ""); return true;
    case -17:
      output = getString(g, ""); return true;
    case -18:
      havefftw = !false; return true;
    case -19:
      countops = !false; return true;
    case -20:
      profile = !false; return true;
    case -21:
      optfile = getString(g, ""); return true;
    case -22:
      stats = !false; return true;
    case -23:
      print_partitioned_source = !false; return true;
    case -24:
      debug = !false; return true;
    case -25:
      fusion = !false; return true;
    case -26:
      blender = !false; return true;
    case -27:
      mencoder = !false; return true;
    case -28:
      compressed = !false; return true;
    case -29:
      frameheight = getInt(g, 0); return true;
    case -30:
      framewidth = getInt(g, 0); return true;
    case 'u':
      unroll = getInt(g, 0); return true;
    case 'p':
      partition_dp = !false; return true;
    case -31:
      partition_ilp = !false; return true;
    case -32:
      partition_greedy = !false; return true;
    case -33:
      partition_greedier = !false; return true;
    case 'L':
      linearpartition = !false; return true;
    case -34:
      dpscaling = !false; return true;
    case -35:
      forceunroll = !false; return true;
    case -36:
      decoupled = !false; return true;
    case -37:
      sync = !false; return true;
    case -38:
      ratematch = !false; return true;
    case -39:
      manuallayout = !false; return true;
    case -40:
      linearanalysis = !false; return true;
    case -41:
      statespace = !false; return true;
    case -42:
      nodatacollapse = !false; return true;
    case -43:
      nolinearcollapse = !false; return true;
    case -44:
      linearreplacement = !false; return true;
    case -45:
      linearreplacement2 = !false; return true;
    case -46:
      linearreplacement3 = !false; return true;
    case -47:
      atlas = !false; return true;
    case 'F':
      frequencyreplacement = !false; return true;
    case -48:
      redundantreplacement = !false; return true;
    case -49:
      removeglobals = !false; return true;
    case -50:
      poptopeek = !false; return true;
    case -51:
      cluster = getInt(g, 0); return true;
    case -52:
      e2 = getInt(g, 0); return true;
    case -53:
      newSimple = getInt(g, 0); return true;
    case -54:
      vectorize = getInt(g, 0); return true;
    case -55:
      cell_vector_library = !false; return true;
    case -56:
      cacheopt = !false; return true;
    case -57:
      l1i = getInt(g, 0); return true;
    case -58:
      l1d = getInt(g, 0); return true;
    case -59:
      l2 = getInt(g, 0); return true;
    case -60:
      peekratio = getInt(g, 0); return true;
    case -61:
      nomult = !false; return true;
    case -62:
      dynamicRatesEverywhere = !false; return true;
    case 'r':
      raw = getInt(g, 0); return true;
    case 'c':
      rawcol = getInt(g, 0); return true;
    case -63:
      streamit = !false; return true;
    case 'N':
      numbers = getInt(g, 0); return true;
    case 'S':
      standalone = !false; return true;
    case -64:
      fixseed = !false; return true;
    case 'M':
      magic_net = !false; return true;
    case -65:
      sjtopipe = !false; return true;
    case 's':
      simulatework = !false; return true;
    case -66:
      clone_with_serialization = !false; return true;
    case -67:
      graph = !false; return true;
    case -68:
      wbs = !false; return true;
    case -69:
      spacetime = !false; return true;
    case -70:
      cell = getInt(g, 0); return true;
    case -71:
      celldyn = !false; return true;
    case -72:
      fission = getInt(g, 0); return true;
    case -73:
      destroyfieldarray = !false; return true;
    case -74:
      outputs = getInt(g, 0); return true;
    case 'i':
      iterations = getInt(g, 0); return true;
    case -75:
      struct = !false; return true;
    case -76:
      backend = getString(g, ""); return true;
    case -77:
      steadymult = getInt(g, 0); return true;
    case 'R':
      rstream = !false; return true;
    case -78:
      doloops = !false; return true;
    case -79:
      absarray = !false; return true;
    case -80:
      modfusion = !false; return true;
    case -81:
      spacedynamic = !false; return true;
    case -82:
      space = !false; return true;
    case -83:
      malloczeros = !false; return true;
    case -84:
      macros = !false; return true;
    case -85:
      nopartition = !false; return true;
    case -86:
      layoutfile = getString(g, ""); return true;
    case -87:
      devassignfile = getString(g, ""); return true;
    case -88:
      backendvalues = getString(g, ""); return true;
    case -89:
      slicethresh = getInt(g, 0); return true;
    case -90:
      asciifileio = !false; return true;
    case -91:
      ssoutputs = getInt(g, 0); return true;
    case -92:
      noswpipe = !false; return true;
    case -93:
      st_cyc_per_wd = getInt(g, 0); return true;
    case -94:
      hwic = !false; return true;
    case -95:
      dup = getInt(g, 0); return true;
    case -96:
      noswitchcomp = !false; return true;
    case -97:
      autoparams = !false; return true;
    case -98:
      greedysched = !false; return true;
    case -99:
      workestunroll = !false; return true;
    case -100:
      rawdcachesize = getInt(g, 0); return true;
    case -101:
      rawicachesize = getInt(g, 0); return true;
    case -102:
      localstoglobals = !false; return true;
    case -103:
      checkpointSIR = !false; return true;
    case -104:
      nompops = !false; return true;
    case -105:
      lime = !false; return true;
    case -106:
      simulator = !false; return true;
    default:
      return super.processOption(code, g);
    }
  }

  public String[] getOptions() {
    String[]   parent = super.getOptions();
    String[]   total = new String[parent.length + 117];
    System.arraycopy(parent, 0, total, 0, parent.length);
    total[parent.length + 0] = "  --beautify:           Beautifies the source code [false]";
    total[parent.length + 1] = "  --help:               Provides help on using compiler [false]";
    total[parent.length + 2] = "  --verbose:            Prints out information during compilation [false]";
    total[parent.length + 3] = "  --java:               Generates java source code instead of class [false]";
    total[parent.length + 4] = "  --encoding:           Sets the character encoding for the source file(s).";
    total[parent.length + 5] = "  --nowrite:            Only checks files, doesn't generate code [false]";
    total[parent.length + 6] = "  --warning:            Maximal level of warnings to be displayed [1]";
    total[parent.length + 7] = "  --nowarn:             Not used, for compatibility purpose [false]";
    total[parent.length + 8] = "  --multi:              Compiles in multi threads mode [false]";
    total[parent.length + 9] = "  --deprecation:        Tests for deprecated members [false]";
    total[parent.length + 10] = "  --proc:               Maximal number of threads to use [2]";
    total[parent.length + 11] = "  --destination:        Writes files to destination";
    total[parent.length + 12] = "  --classpath:          Changes class path to classpath";
    total[parent.length + 13] = "  --lang:               Sets the source language (1.1, 1.2, kopi) [1.1]";
    total[parent.length + 14] = "  --filter:             Warning filter [at.dms.kjc.DefaultFilter]";
    total[parent.length + 15] = "  --output:             Name of output (executable) file.";
    total[parent.length + 16] = "  --havefftw:           Assume existence of sfftw and srfftw libries [false]";
    total[parent.length + 17] = "  --countops:           Output instrumentation hooks for counting each arith op. [false]";
    total[parent.length + 18] = "  --profile:            Output timers for timing execution in each filter. [false]";
    total[parent.length + 19] = "  --optfile:            Specify Java class that performs manual optimizations.";
    total[parent.length + 20] = "  --stats:              Gathers statistics for application characterization. [false]";
    total[parent.length + 21] = "  --print_partitioned_source: Dumps StreamIt program that has same communication pattern as graph AFTER partitioning (for scheduler debugging). [false]";
    total[parent.length + 22] = "  --debug:              Produces debug information (does nothing yet) [false]";
    total[parent.length + 23] = "  --fusion:             Perform maximal filter fusion [false]";
    total[parent.length + 24] = "  --blender:            Hook into Blender [false]";
    total[parent.length + 25] = "  --mencoder:           Hook into MEncoder [false]";
    total[parent.length + 26] = "  --compressed:         Operate directly on Apple Animation-compressed data [false]";
    total[parent.length + 27] = "  --frameheight:        Uncompressed frame height (when operating directly on Apple Animation-compressed data) [720]";
    total[parent.length + 28] = "  --framewidth:         Uncompressed frame width (when operating directly on Apple Animation-compressed data) [480]";
    total[parent.length + 29] = "  --unroll, -u<int>:    Maximum number of loop iterations to unroll [0]";
    total[parent.length + 30] = "  --partition_dp, -p:   Use dynamic programming partitioner to fit stream graph to target [false]";
    total[parent.length + 31] = "  --partition_ilp:      Use ILP partitioner to fit stream graph to target. [false]";
    total[parent.length + 32] = "  --partition_greedy:   Use greedy partitioner to fit stream graph to target. [false]";
    total[parent.length + 33] = "  --partition_greedier: Use greedier partitioner to fit stream graph to target. [false]";
    total[parent.length + 34] = "  --linearpartition, -L: Use the dynamic programming partitioner for guiding linear transforms. [false]";
    total[parent.length + 35] = "  --dpscaling:          Collect theoretical scaling statistics for dynamic programming partitioner. [false]";
    total[parent.length + 36] = "  --forceunroll:        Force unroll to -u amount (even if overflows IMEM). [false]";
    total[parent.length + 37] = "  --decoupled:          Generated dummy communcation code for raw. [false]";
    total[parent.length + 38] = "  --sync:               Turn on sync removal [false]";
    total[parent.length + 39] = "  --ratematch:          Turn on rate matching for Raw [false]";
    total[parent.length + 40] = "  --manuallayout:       Do not run simulated annealing for layout [false]";
    total[parent.length + 41] = "  --linearanalysis:     Perform linear analysis [false]";
    total[parent.length + 42] = "  --statespace:         Perform linear state-space analysis [false]";
    total[parent.length + 43] = "  --nodatacollapse:     Do not collapse splitjoins of data-parallel components at beginning of compilation. [false]";
    total[parent.length + 44] = "  --nolinearcollapse:   If linear analysis is on, do NOT collapse adjacent linear nodes into one.  Just replace individual filters. [false]";
    total[parent.length + 45] = "  --linearreplacement:  Perform linear transformations based on linear analysis. [false]";
    total[parent.length + 46] = "  --linearreplacement2: Perform linear transformation using a level of indirection. [false]";
    total[parent.length + 47] = "  --linearreplacement3: Perform linear transformation using diagonal replacement (for contiguous non-zero regions of matrix.) [false]";
    total[parent.length + 48] = "  --atlas:              Perform linear transformation using ATLAS library for matrix multiplies. [false]";
    total[parent.length + 49] = "  --frequencyreplacement, -F: Convert linear filters to a frequency implementation. [false]";
    total[parent.length + 50] = "  --redundantreplacement: Replace linear filters with linear filters that have no redundan_t_ computation.. [false]";
    total[parent.length + 51] = "  --removeglobals:      While building the Raw main function inline all function calls, if possible, and convert all fields of a filter to locals [false]";
    total[parent.length + 52] = "  --poptopeek:          Convert all Pop expressions into Peek expressions (for uniprocessor only, does nothing for raw). [false]";
    total[parent.length + 53] = "  --cluster:            Compile for a network cluster with <n> nodes. [-1]";
    total[parent.length + 54] = "  --e2:                 Compile for a e2 cluster with <n> nodes. [-1]";
    total[parent.length + 55] = "  --newSimple:          New uni-processor / shared-memory multicore backend [-1]";
    total[parent.length + 56] = "  --vectorize:          Naive vectorization for a machine with <n>-byte vector registers. [-1]";
    total[parent.length + 57] = "  --cell_vector_library: Vectorize math operations using IBMs vector versions [false]";
    total[parent.length + 58] = "  --cacheopt:           Perform cache optimization [false]";
    total[parent.length + 59] = "  --l1i:                Level 1 i-cache size in K for cache optimizations [8]";
    total[parent.length + 60] = "  --l1d:                Level 1 d-cache size in K for cache optmizations [8]";
    total[parent.length + 61] = "  --l2:                 Level 2 cache size in K for cache optimizations [256]";
    total[parent.length + 62] = "  --peekratio:          Increases multiplicity of peeking filters to make sure that (pop * ratio) >= (peek - pop) [-1]";
    total[parent.length + 63] = "  --nomult:             Do not increase multiplicity of partitions [false]";
    total[parent.length + 64] = "  --dynamicRatesEverywhere: For testing performance of dynamic rates [false]";
    total[parent.length + 65] = "  --raw, -r<int>:       Compile for RAW with a square layout, with <n> tiles per side [-1]";
    total[parent.length + 66] = "  --rawcol, -c<int>:    Sets the number of column of tiles. With this set raw specifies the number of rows. [-1]";
    total[parent.length + 67] = "  --streamit:           Compile StreamIt code. [false]";
    total[parent.length + 68] = "  --numbers, -N<int>:   Attempt Generate Raw code that gathers performance numbers, with <n> steady state cycles. [-1]";
    total[parent.length + 69] = "  --standalone, -S:     Use the Raw backend to generate C file for uniprocessor. [false]";
    total[parent.length + 70] = "  --fixseed:            Fix all Random seeds to known values for repeatable testing [false]";
    total[parent.length + 71] = "  --magic_net, -M:      Generate Magic Network Code. [false]";
    total[parent.length + 72] = "  --sjtopipe:           Convert splitjoins to pipelines. [false]";
    total[parent.length + 73] = "  --simulatework, -s:   Uses simulator to measure work required by filters. [false]";
    total[parent.length + 74] = "  --clone_with_serialization: Uses the OLD cloning method that relies on serialization. [false]";
    total[parent.length + 75] = "  --graph:              Outputs graph representation for eclipse plugin [false]";
    total[parent.length + 76] = "  --wbs:                Uses the work based simulator for laying out the communication instructions. [false]";
    total[parent.length + 77] = "  --spacetime:          Uses the spacetime partitioner. [false]";
    total[parent.length + 78] = "  --cell:               Compile for Cell with <n> SPUs. [-1]";
    total[parent.length + 79] = "  --celldyn:            Compile to Cell for use with dynamic scheduler. [false]";
    total[parent.length + 80] = "  --fission:            Perform vertical fission wherever possible, with maximum of <n>-way fiss per filter. [-1]";
    total[parent.length + 81] = "  --destroyfieldarray:  Destroy field arrays [false]";
    total[parent.length + 82] = "  --outputs:            Run the application until it produces <n> outputs (RAW only). [-1]";
    total[parent.length + 83] = "  --iterations, -i<int>: Run the application for <n> steady-state iterations.. [-1]";
    total[parent.length + 84] = "  --struct:             Handle Structures. Experimental feature. [false]";
    total[parent.length + 85] = "  --backend:            Class name of the StreamIt compiler backend to run.";
    total[parent.length + 86] = "  --steadymult:         For the SpaceTime RAW backend, multiply the steady-state multiplicities by i [1]";
    total[parent.length + 87] = "  --rstream, -R:        Generate code for the RStream front-end. [false]";
    total[parent.length + 88] = "  --doloops:            Generate doloops for for loops where possible in the rstream backend. [false]";
    total[parent.length + 89] = "  --absarray:           Use abstract array syntax and semantics instead of C arrays. [false]";
    total[parent.length + 90] = "  --modfusion:          Use circular buffer with mod operations when fusing pipelines. [false]";
    total[parent.length + 91] = "  --spacedynamic:       Use the space multiplexing Raw backend with dynamic rate support. [false]";
    total[parent.length + 92] = "  --space:              Use the space Raw backend (without dynamic rate support). [false]";
    total[parent.length + 93] = "  --malloczeros:        Use mallocs instead of callocs to initial memory (malloc will zero memory). [false]";
    total[parent.length + 94] = "  --macros:             Convert small functions to macros, effectively inlining them. [false]";
    total[parent.length + 95] = "  --nopartition:        For the space-dynamic backend, do not attempt to partition each static subgraph. [false]";
    total[parent.length + 96] = "  --layoutfile:         For the space-dynamic backend, specify a layout file (a new-line separated list of tiles).";
    total[parent.length + 97] = "  --devassignfile:      For the space-dynamic backend, specify a file that assigns devices to ports (a new-line separated list of tiles).";
    total[parent.length + 98] = "  --backendvalues:      For debugging, specify class with package:value for static variable";
    total[parent.length + 99] = "  --slicethresh:        For the spacetime backend, set the slice threshold to be <int> percent. [101]";
    total[parent.length + 100] = "  --asciifileio:        Use ascii format for file input and output. [false]";
    total[parent.length + 101] = "  --ssoutputs:          When using --numbers on a dynamic-rate app, specify outputs per steady state. [-1]";
    total[parent.length + 102] = "  --noswpipe:           For spacetime, do not software pipeline the steady state. [false]";
    total[parent.length + 103] = "  --st_cyc_per_wd:      For spacetime, define the bandwidth in cycles per word for the streaming memories attached to the raw chip. [1]";
    total[parent.length + 104] = "  --hwic:               When targeting the raw simulator, enable H/W icaching. [false]";
    total[parent.length + 105] = "  --dup:                For the SpaceTime Backend, duplicate all stateless filters n times. [-1]";
    total[parent.length + 106] = "  --noswitchcomp:       For the SpaceTime Backend, turn off switch instruction compression. [false]";
    total[parent.length + 107] = "  --autoparams:         For the SpaceTime Backend, automatically calculate parameters. [false]";
    total[parent.length + 108] = "  --greedysched:        For the SpaceTime Backend, use a greedy bin packing algorithm to calculate the schedule. [false]";
    total[parent.length + 109] = "  --workestunroll:      For the SpaceTime Backend, perform unrolling on filters for work estimation. [false]";
    total[parent.length + 110] = "  --rawdcachesize:      For Raw, set the dcache size for each tile (use a power of 2). [32768]";
    total[parent.length + 111] = "  --rawicachesize:      For Raw, set the dcache size for each tile (use a power of 2). [32768]";
    total[parent.length + 112] = "  --localstoglobals:    Convert local variables to global variables (can avoid stack overflow). [false]";
    total[parent.length + 113] = "  --checkpointSIR:      Generate STR code from SIR. [false]";
    total[parent.length + 114] = "  --nompops:            Do not convert existing loops that simply pop() to pop(n) statements. [false]";
    total[parent.length + 115] = "  --lime:               Generate code for Lime compiler. [false]";
    total[parent.length + 116] = "  --simulator:          Compile for simulator [false]";
    
    return total;
  }


  public String getShortOptions() {
    return "u:pLFr:c:N:SMsi:R" + super.getShortOptions();
  }


  public void version() {
    System.out.println();
  }


  public void usage() {
    System.err.println("usage: at.dms.kjc.Main [option]* [--help] <java-files>");
  }


  public void help() {
    System.err.println("usage: at.dms.kjc.Main [option]* [--help] <java-files>");
    printOptions();
    System.err.println();
    version();
    System.err.println();
    System.err.println("This program is part of the Kopi Suite.");
    System.err.println("For more info, please see: http://www.dms.at/kopi");
  }

  public LongOpt[] getLongOptions() {
    LongOpt[]  parent = super.getLongOptions();
    LongOpt[]  total = new LongOpt[parent.length + LONGOPTS.length];
    
    System.arraycopy(parent, 0, total, 0, parent.length);
    System.arraycopy(LONGOPTS, 0, total, parent.length, LONGOPTS.length);
    
    return total;
  }

  private static final LongOpt[] LONGOPTS = {
    new LongOpt("beautify", LongOpt.NO_ARGUMENT, null, -2),
    new LongOpt("help", LongOpt.NO_ARGUMENT, null, -3),
    new LongOpt("verbose", LongOpt.NO_ARGUMENT, null, -4),
    new LongOpt("java", LongOpt.NO_ARGUMENT, null, -5),
    new LongOpt("encoding", LongOpt.REQUIRED_ARGUMENT, null, -6),
    new LongOpt("nowrite", LongOpt.NO_ARGUMENT, null, -7),
    new LongOpt("warning", LongOpt.OPTIONAL_ARGUMENT, null, -8),
    new LongOpt("nowarn", LongOpt.NO_ARGUMENT, null, -9),
    new LongOpt("multi", LongOpt.NO_ARGUMENT, null, -10),
    new LongOpt("deprecation", LongOpt.NO_ARGUMENT, null, -11),
    new LongOpt("proc", LongOpt.REQUIRED_ARGUMENT, null, -12),
    new LongOpt("destination", LongOpt.REQUIRED_ARGUMENT, null, -13),
    new LongOpt("classpath", LongOpt.REQUIRED_ARGUMENT, null, -14),
    new LongOpt("lang", LongOpt.REQUIRED_ARGUMENT, null, -15),
    new LongOpt("filter", LongOpt.REQUIRED_ARGUMENT, null, -16),
    new LongOpt("output", LongOpt.REQUIRED_ARGUMENT, null, -17),
    new LongOpt("havefftw", LongOpt.NO_ARGUMENT, null, -18),
    new LongOpt("countops", LongOpt.NO_ARGUMENT, null, -19),
    new LongOpt("profile", LongOpt.NO_ARGUMENT, null, -20),
    new LongOpt("optfile", LongOpt.REQUIRED_ARGUMENT, null, -21),
    new LongOpt("stats", LongOpt.NO_ARGUMENT, null, -22),
    new LongOpt("print_partitioned_source", LongOpt.NO_ARGUMENT, null, -23),
    new LongOpt("debug", LongOpt.NO_ARGUMENT, null, -24),
    new LongOpt("fusion", LongOpt.NO_ARGUMENT, null, -25),
    new LongOpt("blender", LongOpt.NO_ARGUMENT, null, -26),
    new LongOpt("mencoder", LongOpt.NO_ARGUMENT, null, -27),
    new LongOpt("compressed", LongOpt.NO_ARGUMENT, null, -28),
    new LongOpt("frameheight", LongOpt.REQUIRED_ARGUMENT, null, -29),
    new LongOpt("framewidth", LongOpt.REQUIRED_ARGUMENT, null, -30),
    new LongOpt("unroll", LongOpt.REQUIRED_ARGUMENT, null, 'u'),
    new LongOpt("partition_dp", LongOpt.NO_ARGUMENT, null, 'p'),
    new LongOpt("partition_ilp", LongOpt.NO_ARGUMENT, null, -31),
    new LongOpt("partition_greedy", LongOpt.NO_ARGUMENT, null, -32),
    new LongOpt("partition_greedier", LongOpt.NO_ARGUMENT, null, -33),
    new LongOpt("linearpartition", LongOpt.NO_ARGUMENT, null, 'L'),
    new LongOpt("dpscaling", LongOpt.NO_ARGUMENT, null, -34),
    new LongOpt("forceunroll", LongOpt.NO_ARGUMENT, null, -35),
    new LongOpt("decoupled", LongOpt.NO_ARGUMENT, null, -36),
    new LongOpt("sync", LongOpt.NO_ARGUMENT, null, -37),
    new LongOpt("ratematch", LongOpt.NO_ARGUMENT, null, -38),
    new LongOpt("manuallayout", LongOpt.NO_ARGUMENT, null, -39),
    new LongOpt("linearanalysis", LongOpt.NO_ARGUMENT, null, -40),
    new LongOpt("statespace", LongOpt.NO_ARGUMENT, null, -41),
    new LongOpt("nodatacollapse", LongOpt.NO_ARGUMENT, null, -42),
    new LongOpt("nolinearcollapse", LongOpt.NO_ARGUMENT, null, -43),
    new LongOpt("linearreplacement", LongOpt.NO_ARGUMENT, null, -44),
    new LongOpt("linearreplacement2", LongOpt.NO_ARGUMENT, null, -45),
    new LongOpt("linearreplacement3", LongOpt.NO_ARGUMENT, null, -46),
    new LongOpt("atlas", LongOpt.NO_ARGUMENT, null, -47),
    new LongOpt("frequencyreplacement", LongOpt.NO_ARGUMENT, null, 'F'),
    new LongOpt("redundantreplacement", LongOpt.NO_ARGUMENT, null, -48),
    new LongOpt("removeglobals", LongOpt.NO_ARGUMENT, null, -49),
    new LongOpt("poptopeek", LongOpt.NO_ARGUMENT, null, -50),
    new LongOpt("cluster", LongOpt.REQUIRED_ARGUMENT, null, -51),
    new LongOpt("e2", LongOpt.REQUIRED_ARGUMENT, null, -52),
    new LongOpt("newSimple", LongOpt.REQUIRED_ARGUMENT, null, -53),
    new LongOpt("vectorize", LongOpt.REQUIRED_ARGUMENT, null, -54),
    new LongOpt("cell_vector_library", LongOpt.NO_ARGUMENT, null, -55),
    new LongOpt("cacheopt", LongOpt.NO_ARGUMENT, null, -56),
    new LongOpt("l1i", LongOpt.REQUIRED_ARGUMENT, null, -57),
    new LongOpt("l1d", LongOpt.REQUIRED_ARGUMENT, null, -58),
    new LongOpt("l2", LongOpt.REQUIRED_ARGUMENT, null, -59),
    new LongOpt("peekratio", LongOpt.REQUIRED_ARGUMENT, null, -60),
    new LongOpt("nomult", LongOpt.NO_ARGUMENT, null, -61),
    new LongOpt("dynamicRatesEverywhere", LongOpt.NO_ARGUMENT, null, -62),
    new LongOpt("raw", LongOpt.REQUIRED_ARGUMENT, null, 'r'),
    new LongOpt("rawcol", LongOpt.REQUIRED_ARGUMENT, null, 'c'),
    new LongOpt("streamit", LongOpt.NO_ARGUMENT, null, -63),
    new LongOpt("numbers", LongOpt.REQUIRED_ARGUMENT, null, 'N'),
    new LongOpt("standalone", LongOpt.NO_ARGUMENT, null, 'S'),
    new LongOpt("fixseed", LongOpt.NO_ARGUMENT, null, -64),
    new LongOpt("magic_net", LongOpt.NO_ARGUMENT, null, 'M'),
    new LongOpt("sjtopipe", LongOpt.NO_ARGUMENT, null, -65),
    new LongOpt("simulatework", LongOpt.NO_ARGUMENT, null, 's'),
    new LongOpt("clone_with_serialization", LongOpt.NO_ARGUMENT, null, -66),
    new LongOpt("graph", LongOpt.NO_ARGUMENT, null, -67),
    new LongOpt("wbs", LongOpt.NO_ARGUMENT, null, -68),
    new LongOpt("spacetime", LongOpt.NO_ARGUMENT, null, -69),
    new LongOpt("cell", LongOpt.REQUIRED_ARGUMENT, null, -70),
    new LongOpt("celldyn", LongOpt.NO_ARGUMENT, null, -71),
    new LongOpt("fission", LongOpt.REQUIRED_ARGUMENT, null, -72),
    new LongOpt("destroyfieldarray", LongOpt.NO_ARGUMENT, null, -73),
    new LongOpt("outputs", LongOpt.REQUIRED_ARGUMENT, null, -74),
    new LongOpt("iterations", LongOpt.REQUIRED_ARGUMENT, null, 'i'),
    new LongOpt("struct", LongOpt.NO_ARGUMENT, null, -75),
    new LongOpt("backend", LongOpt.REQUIRED_ARGUMENT, null, -76),
    new LongOpt("steadymult", LongOpt.REQUIRED_ARGUMENT, null, -77),
    new LongOpt("rstream", LongOpt.NO_ARGUMENT, null, 'R'),
    new LongOpt("doloops", LongOpt.NO_ARGUMENT, null, -78),
    new LongOpt("absarray", LongOpt.NO_ARGUMENT, null, -79),
    new LongOpt("modfusion", LongOpt.NO_ARGUMENT, null, -80),
    new LongOpt("spacedynamic", LongOpt.NO_ARGUMENT, null, -81),
    new LongOpt("space", LongOpt.NO_ARGUMENT, null, -82),
    new LongOpt("malloczeros", LongOpt.NO_ARGUMENT, null, -83),
    new LongOpt("macros", LongOpt.NO_ARGUMENT, null, -84),
    new LongOpt("nopartition", LongOpt.NO_ARGUMENT, null, -85),
    new LongOpt("layoutfile", LongOpt.REQUIRED_ARGUMENT, null, -86),
    new LongOpt("devassignfile", LongOpt.REQUIRED_ARGUMENT, null, -87),
    new LongOpt("backendvalues", LongOpt.REQUIRED_ARGUMENT, null, -88),
    new LongOpt("slicethresh", LongOpt.REQUIRED_ARGUMENT, null, -89),
    new LongOpt("asciifileio", LongOpt.NO_ARGUMENT, null, -90),
    new LongOpt("ssoutputs", LongOpt.REQUIRED_ARGUMENT, null, -91),
    new LongOpt("noswpipe", LongOpt.NO_ARGUMENT, null, -92),
    new LongOpt("st_cyc_per_wd", LongOpt.REQUIRED_ARGUMENT, null, -93),
    new LongOpt("hwic", LongOpt.NO_ARGUMENT, null, -94),
    new LongOpt("dup", LongOpt.REQUIRED_ARGUMENT, null, -95),
    new LongOpt("noswitchcomp", LongOpt.NO_ARGUMENT, null, -96),
    new LongOpt("autoparams", LongOpt.NO_ARGUMENT, null, -97),
    new LongOpt("greedysched", LongOpt.NO_ARGUMENT, null, -98),
    new LongOpt("workestunroll", LongOpt.NO_ARGUMENT, null, -99),
    new LongOpt("rawdcachesize", LongOpt.REQUIRED_ARGUMENT, null, -100),
    new LongOpt("rawicachesize", LongOpt.REQUIRED_ARGUMENT, null, -101),
    new LongOpt("localstoglobals", LongOpt.NO_ARGUMENT, null, -102),
    new LongOpt("checkpointSIR", LongOpt.NO_ARGUMENT, null, -103),
    new LongOpt("nompops", LongOpt.NO_ARGUMENT, null, -104),
    new LongOpt("lime", LongOpt.NO_ARGUMENT, null, -105),
    new LongOpt("simulator", LongOpt.NO_ARGUMENT, null, -106)
  };
}
