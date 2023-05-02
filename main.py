from tkinter import *
from tkinter import ttk

from m1.m1app import M1App

if __name__ == '__main__':
    root = Tk()
    root.title("Matrix Clock")
    root.geometry("600x400")
    root.resizable(0, 0)

    app = M1App(root)

    root.mainloop()