//#Safe
var N : int;
var x : int;

procedure ULTIMATE.start()
modifies x;
{
  x := 0;

  fork 1   thread1();
  fork 2,2 thread2();
  join 1;
  join 2,2;

  assert x == 0;
}

procedure thread1()
modifies x;
{
  var i : int;

  i := 0;
  while (i < N) {
    x := x + 1;
    i := i + 1;
  }
}

procedure thread2()
modifies x;
{
  var i : int;

  i := 0;
  while (i < N) {
    x := x - 1;
    i := i + 1;
  }
}