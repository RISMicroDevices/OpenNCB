init:
	git submodule update --init
	cd external/rocket-chip && git submodule update --init cde

compile:
	mill -i OpenNCB.compile

clean:
	rm -rf ./build

bsp:
	mill -i mill.bsp.BSP/install

idea:
	mill -i mill.scalalib.GenIdea/idea

reformat:
	mill -i __.reformat

checkformat:
	mill -i __.checkFormat
