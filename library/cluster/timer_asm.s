  .section  ".text"
	.align 2
 	.globl TB
	.type	TB, @function
TB:
	mftbu 0
	mftb 4
	mftbu 3
	cmpw cr0,0,3
	beqlr- cr0
	b TB
	.globl TBl
	.type	TBl, @function
TBl:
	mftb 3
	blr
