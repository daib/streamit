#!/usr/bin/perl

use strict;
my $sourcePath = "/Users/daib/prog/misc/streamit";
my @dirs = glob($sourcePath."/*");

my $opts = "--simulator --streamit ";

foreach(@dirs)
{
    #print $_."\n";
    my $currentDir = $_;
    my @javas = glob($currentDir."/*.java");

    foreach(@javas) 
    {
        #print $_."\n";
        if(/\/(\w+)\/(\w+).java/) {
            my $args = $opts.$sourcePath."/".$1."/".$2.".java";
            print $args."\n";
            my $name = $2; 
            my @output = `java -Xmx1024m -classpath ./bin at.dms.kjc.Main $args`;
            for(@output) {
                if(/Computational time:\s*(\d+)\s+n vertices\s+(\d+)/) {
                    print $name." ".$1." ".$2."\n";     
                }
            }
        }
    }
}
#print $args."\n";
