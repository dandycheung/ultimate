//#Safe
var A : [int]int;
var B : [int]bool;

procedure ULTIMATE.start()
modifies A, B;
{
  fork 1           thread1(1);
  fork 2,2         thread1(2);
  fork 3,3,3       thread1(3);
  fork 4,4,4,4     thread1(4);
  fork 5,5,5,5,5   thread1(5);
  fork 6,6,6,6,6,6 thread1(6);
  join 1;
  join 2,2;
  join 3,3,3;
  join 4,4,4,4;
  join 5,5,5,5,5;
  join 6,6,6,6,6,6;
}

procedure thread1(x : int)
modifies A, B;
{
  var i : int;
  var b : bool;

  i := 0;
  while (true) {
    call b := acquire(i);
    if (b) {
      A[i] := x;
      assert A[i] == x;
    }
    i := i + 1;
  }
}

procedure acquire(i : int) returns (b : bool)
modifies B;
{
  atomic {
    b := B[i];
    if (b) {
      B[i] := false;
    }
  }
}