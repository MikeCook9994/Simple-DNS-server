#!/bin/bash

echo "dig -p 8053 @localhost A wisc.edu"
dig -p 8053 @localhost A wisc.edu

echo "dig -p 8053 @localhost A wisc.edu"
dig -p 8053 @localhost A wisc.edu

echo "dig -p 8053 @localhost A www.pinterest.com"
dig -p 8053 @localhost A www.pinterest.com

echo "dig -p 8053 @localhost CNAME www.pinterest.com"
dig -p 8053 @localhost CNAME www.pinterest.com

echo "dig -p 8053 @localhost NS wisc.edu"
dig -p 8053 @localhost NS wisc.edu

echo "dig -p 8053 @localhost A www.code.org"
dig -p 8053 @localhost A www.code.org

echo "dig +norecurse -p 8053 @localhost A wisc.edu"
dig +norecurse -p 8053 @localhost A wisc.edu

echo "dig -p 8053 @localhost A www.netflix.com"
dig -p 8053 @localhost A www.netflix.com
