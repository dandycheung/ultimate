//#Safe

var A, B : [int]int;
var N : int;

var i1, i2 : int;
var sum1, sum2 : int;


procedure sum1() returns (x : int)
modifies i1, sum1;
{
  sum1 := 0;

  i1 := 0;
  while (i1 < N) {
    atomic {
    sum1 := sum1 + A[i1];
    i1 := i1 + 1;
    }
  }
}


procedure sum2() returns (y : int)
modifies i2, sum2;
{
  sum2 := 0;

  i2 := 0;
  while (i2 < N) {
    atomic{
    sum2 := sum2 + A[i2];
    i2 := i2 + 1;
    }
  }
}

procedure bank()
modifies B;
{
  while (*) {
    B[i1-1] := sum1;
    B[i2-1] := sum2;
  }
}

procedure ULTIMATE.start()
modifies A, B, i1, i2, sum1, sum2;
{
  var x, y : int;

  fork 1   sum1();
  fork 2,2 sum2();
  fork 3,3,3 bank();
  join 1;
  join 2,2;

  assert sum1 == sum2;
}
