#!/bin/bash

cd ../../..
PROJECT_PATH=$(pwd)

FILE=$PROJECT_PATH/src/main/clojure/numeric/expresso/solve.clj
ORIGINAL=$PROJECT_PATH/src/mutants/rmb6/solve.original.clj


MESSAGE[1]='Interprocedural Operator. Value Replacement'
MESSAGE[2]='Interprocedural Operator. Value Replacement'
MESSAGE[3]='Interprocedural Operator. Value Replacement'
MESSAGE[4]='Interprocedural Operator. Value Replacement'
MESSAGE[5]='Interprocedural Operator. Value Replacement'
MESSAGE[6]='Interprocedural Operator. Value Replacement'
MESSAGE[7]='Arithmetic Operator Replacement'
MESSAGE[8]='Arithmetic Operator Replacement'
MESSAGE[9]='Arithmetic Operator Replacement'
MESSAGE[10]='Arithmetic Operator Replacement'
MESSAGE[11]='Arithmetic Operator Replacement'
MESSAGE[12]='Arithmetic Operator Replacement'
MESSAGE[13]='Arithmetic Operator Replacement'
MESSAGE[14]='Arithmetic Operator Replacement'
MESSAGE[15]='Arithmetic Operator Replacement'
MESSAGE[16]='Arithmetic Operator Replacement'
MESSAGE[17]='Arithmetic Operator Replacement'
MESSAGE[18]='Arithmetic Operator Replacement'
MESSAGE[19]='Arithmetic Operator Replacement'
MESSAGE[20]='Arithmetic Operator Replacement'
MESSAGE[21]='Scalar Variable Replacement'
MESSAGE[22]='Scalar Variable Replacement'
MESSAGE[23]='Scalar Variable Replacement'
MESSAGE[24]='Scalar Variable Replacement'
MESSAGE[25]='Scalar Variable Replacement'

echo -e "===================================="
for i in {1..25}; do
	MUTANT=$PROJECT_PATH/src/mutants/rmb6/solve.rmb6.$i.clj
	echo -e "Mutant $i: ${MESSAGE[$i]}"
	echo -e "------------------------------------"
	echo -e "Diff Result for Mutant $i:"
	echo -e "------------------------------------"
	diff -c $ORIGINAL $MUTANT
	cp $MUTANT $FILE
	echo -e "------------------------------------"
	echo -e "Test Results for Mutant $i:"
	echo -e "------------------------------------"
	lein test |grep -A 2 -e "Ran " -e "FAIL in" -e "ERROR in" > $PROJECT_PATH/src/results/rmb6/solve.rmb6.$i.txt
	echo -e "\n"
	echo -e "===================================="
done

cp $ORIGINAL $FILE


